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
import uk.me.michaellloydlee.recruiter.domain.screening.RecruiterHeuristics
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
import java.time.Instant
import java.util.Base64

/**
 * The service of a run of the pipeline.
 *
 * @param gmail the engine of the API of Gmail.
 * @param model the engine of the models of Ollama.
 * @param log the sink of the log of the run.
 * @param requirements the requirements of the job seeker.
 * @return the service of the run.
 */
suspend fun service(
    gmail: MockEngine,
    model: MockEngine,
    log: StringBuilder = StringBuilder(),
    requirements: JobRequirements = REQUIREMENTS,
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
                heuristics = RecruiterHeuristics(),
                verifier = DecisionModelVerifier(modelClient),
                stateManager = stateManager,
            ),
        evaluation =
            EvaluationService(
                evaluator = DecisionModelEvaluator(modelClient),
                attachments = AttachmentTextReader(messages, StructuredLogger(log)),
            ),
        drafts = DraftWriter(client = chatClient, drafts = GmailDraftsApi(api)),
        requirements = requirements,
        stateManager = stateManager,
        usageCounter = usage,
        logger = StructuredLogger(log),
    )
}

/**
 * The engine of the models.
 *
 * The model answers the recruiter question and the four criteria of the
 * evaluation.
 *
 * @param officeDays what the model answers about the days of office.
 * @param answered whether the model answers about the job description.
 * @param chatAnswers whether the model of the draft answers.
 * @param jdMatches what the model answers about the job description.
 * @param confidenceOfTheProfile how sure the model is about the profile.
 * @return the engine of the models.
 */
fun modelEngine(
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

/**
 * The engine of the API of Gmail.
 *
 * @param listing whether the messages are the alerts of a job board.
 * @param labels the labels the messages carry.
 * @param modifyFails whether the labels of a message cannot be changed.
 * @return the engine of the API of Gmail.
 */
fun gmailEngine(
    listing: Boolean = false,
    labels: List<String> = listOf("INBOX"),
    modifyFails: Boolean = false,
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

            request.url.encodedPath.endsWith("/modify") && modifyFails -> {
                respond(
                    content = """{"error":"the labels cannot be changed"}""",
                    status = HttpStatusCode.InternalServerError,
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
 * The id of a label of the mailbox.
 *
 * @param label the path of the label.
 * @return the id of the label.
 */
fun idOf(label: String): String = "Label_${label.substringAfterLast('/').lowercase()}"

/**
 * The id of the message the request asks for.
 *
 * @param request the request of the API.
 * @return the id of the message.
 */
fun idOf(request: HttpRequestData): String =
    request.url.encodedPath
        .substringAfterLast("/users/me/messages/")
        .substringBefore("/")

/**
 * A message of the mailbox.
 *
 * @param id the id of the message.
 * @param listing whether the message is the alert of a job board.
 * @param labels the labels the message carries.
 * @return the message, as the API answers it.
 */
fun message(
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
 *
 * @param id the id of the message.
 * @return the body of the message, encoded for the API.
 */
fun bodyOf(id: String): String = Base64.getUrlEncoder().encodeToString("Looking for a developer $id".toByteArray())

/**
 * The token of the tests.
 *
 * @return the token of the API of Gmail.
 */
fun token(): OAuth2Token =
    OAuth2Token(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Instant.now().plusSeconds(600),
    )

/**
 * The headers of a JSON answer of the API.
 *
 * @return the headers of a JSON answer.
 */
fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/** The moment the run of the tests looks at. */
internal val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")

/** The requirements of the job seeker of the tests. */
internal val REQUIREMENTS: JobRequirements =
    JobRequirements(
        minimumPay = null,
        maximumOfficeDays = 2,
        profile = "Senior Kotlin engineer",
    )

/** The requirements of a job seeker who has a target pay. */
internal val REQUIREMENTS_WITH_A_TARGET: JobRequirements =
    JobRequirements(
        minimumPay = PayRequirement(amount = 120_000.0, currency = "EUR"),
        maximumOfficeDays = 2,
        profile = "Senior Kotlin engineer",
    )

/** The window of the run of the tests. */
internal val WINDOW: RunTarget = RunTarget.Window(NOW.minusSeconds(86400))

/** The answer of the decision model of the tests. */
internal const val DECISION_ANSWER =
    """{"model":"clef:cloud","answers":{},"usage":{"input_tokens":10,"output_tokens":1}}"""

/** The answer of the chat model of the tests. */
internal const val CHAT_ANSWER =
    """{"message":{"role":"assistant","content":"the reply of the model"},"done":true}"""

/** The list of the messages of the tests. */
internal const val MESSAGE_LIST =
    """{"messages":[{"id":"first","threadId":"thread-1"},{"id":"second","threadId":"thread-2"}]}"""

/** The parser of the answers of the API. */
internal val JSON: Json = Json { ignoreUnknownKeys = true }
