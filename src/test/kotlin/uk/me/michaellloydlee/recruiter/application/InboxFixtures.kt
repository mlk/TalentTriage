package uk.me.michaellloydlee.recruiter.application

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.me.michaellloydlee.recruiter.application.draft.DraftWriter
import uk.me.michaellloydlee.recruiter.application.evaluation.AttachmentTextReader
import uk.me.michaellloydlee.recruiter.application.evaluation.DecisionModelEvaluator
import uk.me.michaellloydlee.recruiter.application.evaluation.EvaluationService
import uk.me.michaellloydlee.recruiter.application.screening.DecisionModelVerifier
import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.gmail.RunTarget
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiClient
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailDraftsApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelStateManager
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelsApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Token
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.RefreshingAccessTokenProvider
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.TestOAuth2Client
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.temporaryTokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.llm.ModelUsageCounter
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.notification.WebhookNotifier
import java.time.Instant
import java.util.Base64

internal val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")

internal val WINDOW: RunTarget = RunTarget.Window(NOW.minusSeconds(86400))

internal val REQUIREMENTS: JobRequirements =
    JobRequirements(
        minimumPay = null,
        maximumOfficeDays = 2,
        profile = "Senior Kotlin engineer",
    )

internal val REQUIREMENTS_WITH_A_TARGET: JobRequirements =
    JobRequirements(
        minimumPay = PayRequirement(amount = 120_000.0, currency = "EUR"),
        maximumOfficeDays = 2,
        profile = "Senior Kotlin engineer",
    )

internal val JSON: Json = Json { ignoreUnknownKeys = true }

internal const val DECISION_ANSWER: String =
    """{"model":"clef:cloud","answers":{},"usage":{"input_tokens":10,"output_tokens":1}}"""

internal const val CHAT_ANSWER: String =
    """{"message":{"role":"assistant","content":"the reply of the model"},"done":true}"""

internal const val MESSAGE_LIST: String =
    """{"messages":[{"id":"first","threadId":"thread-1"},{"id":"second","threadId":"thread-2"}]}"""

internal fun gmailEngine(
    listing: Boolean = false,
    labels: List<String> = listOf("INBOX"),
): MockEngine =
    MockEngine { request ->
        when {
            request.url.encodedPath.endsWith("/users/me/messages") -> {
                respond(
                    content = MESSAGE_LIST,
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }

            request.url.encodedPath.endsWith("/users/me/labels") && request.method.value == "GET" -> {
                respond(
                    content = """{"labels":[{"id":"Label_worthfollowup","name":"Recruiters/WorthFollowUp"}]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }

            request.url.encodedPath.endsWith("/users/me/labels") -> {
                val name =
                    Json
                        .parseToJsonElement((request.body as TextContent).text)
                        .jsonObject["name"]!!
                        .jsonPrimitive
                        .content
                respond(
                    content = """{"id":"${idOf(name)}","name":"$name"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }

            request.url.encodedPath.endsWith("/users/me/drafts") -> {
                respond(
                    content = """{"id":"draft-id"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }

            else -> {
                respond(
                    content = message(idOf(request), listing, labels),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }
    }

/**
 * The model answers the recruiter question and the four criteria of the
 * evaluation.
 */
internal fun modelEngine(
    officeDays: String = "matches",
    answered: Boolean = true,
    chatAnswers: Boolean = true,
    jdMatches: String = "matches",
    confidenceOfTheProfile: Double = 0.9,
): MockEngine =
    MockEngine { request ->
        if (request.url.encodedPath.endsWith("/api/chat")) {
            respond(
                content = CHAT_ANSWER,
                status = if (chatAnswers) HttpStatusCode.OK else HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        } else {
            val body = (request.body as TextContent).text
            val questions = Json.parseToJsonElement(body).jsonObject["questions"]!!.jsonObject
            val answer =
                if (questions.containsKey("recruiter")) {
                    """"recruiter":{"type":"choice","choice":"from a recruiter","confidence":0.9}"""
                } else if (answered) {
                    questions.keys.joinToString(",") { name ->
                        val choice =
                            when (name) {
                                "jd_matches" -> jdMatches
                                "office_days" -> officeDays
                                else -> "does not match"
                            }
                        val confidence = if (name == "jd_matches") confidenceOfTheProfile else 0.9
                        """"$name":{"type":"choice","choice":"$choice","confidence":$confidence}"""
                    }
                } else {
                    ""
                }
            respond(
                content = DECISION_ANSWER.replace("{}", "{$answer}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    }

internal fun webhookEngine(status: HttpStatusCode = HttpStatusCode.OK): MockEngine =
    MockEngine {
        respond(content = "", status = status)
    }

internal fun webhookNotifier(engine: MockEngine): WebhookNotifier =
    WebhookNotifier(
        httpClient =
            HttpClient(engine) {
                install(HttpTimeout)
                install(ContentNegotiation) { json(JSON) }
            },
        url = "http://webhook.example/hook",
    )

internal suspend fun service(
    gmail: MockEngine,
    model: MockEngine,
    log: StringBuilder = StringBuilder(),
    requirements: JobRequirements = REQUIREMENTS,
    notifier: WebhookNotifier? = null,
): ProcessInboxService {
    val api =
        GmailApiClient(
            httpClient = HttpClient(gmail) { install(ContentNegotiation) { json(JSON) } },
            tokens =
                RefreshingAccessTokenProvider(
                    refresher = TestOAuth2Client().client,
                    tokenStore = temporaryTokenStore(token()),
                ),
            baseUrl = "https://gmail.example/v1",
        )
    val messages = GmailMessagesApi(api)
    val stateManager = GmailLabelStateManager(messages, GmailLabelsApi(api))
    val usage = ModelUsageCounter()
    val modelClient =
        OllamaDecisionClient(
            httpClient =
                HttpClient(model) {
                    install(HttpTimeout)
                    install(ContentNegotiation) { json(JSON) }
                },
            usage = usage,
        )
    val chatClient =
        OllamaChatClient(
            httpClient =
                HttpClient(model) {
                    install(HttpTimeout)
                    install(ContentNegotiation) { json(JSON) }
                },
            usage = usage,
        )
    return ProcessInboxService(
        messages = messages,
        screening =
            ScreeningService(
                heuristics =
                    uk.me.michaellloydlee.recruiter.domain.screening
                        .RecruiterHeuristics(),
                verifier = DecisionModelVerifier(modelClient),
                stateManager = stateManager,
            ),
        evaluation =
            EvaluationService(
                evaluator = DecisionModelEvaluator(modelClient),
                attachments = AttachmentTextReader(messages, StructuredLogger(log)),
                stateManager = stateManager,
            ),
        drafts = DraftWriter(client = chatClient, drafts = GmailDraftsApi(api)),
        requirements = requirements,
        stateManager = stateManager,
        notifier = notifier,
        usageCounter = usage,
        logger = StructuredLogger(log),
    )
}

private fun idOf(label: String): String = "Label_${label.substringAfterLast('/').lowercase()}"

/**
 * The id of the message the request asks for.
 */
private fun idOf(request: HttpRequestData): String =
    request.url.encodedPath
        .substringAfterLast("/users/me/messages/")
        .substringBefore("/")

private fun message(
    id: String,
    listing: Boolean = false,
    labels: List<String> = listOf("INBOX"),
): String {
    val sender =
        if (listing) {
            "LinkedIn Job Alerts &lt;jobalerts-noreply@linkedin.com&gt;"
        } else {
            "recruiter@example.com"
        }
    val text =
        """
        {
          "id": "$id",
          "threadId": "thread-$id",
          "labelIds": [${labels.joinToString(",") { label -> "\"$label\"" }}],
          "payload": {
            "mimeType": "text/plain",
            "headers": [
              {"name": "From", "value": "$sender"},
              {"name": "Subject", "value": "Your CV"}
            ],
            "body": {"data": "${bodyOf(id)}"}
          }
        }
        """.trimIndent()
    return text
}

/**
 * The body of a message, it names the message it belongs to, so a test
 * that fetches two messages can tell them apart.
 */
private fun bodyOf(id: String): String =
    Base64.getUrlEncoder().encodeToString("Looking for a developer $id".toByteArray())

private fun token(): OAuth2Token =
    OAuth2Token(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Instant.now().plusSeconds(600),
    )

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
