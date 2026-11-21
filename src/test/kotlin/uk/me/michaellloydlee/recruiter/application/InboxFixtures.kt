package uk.me.michaellloydlee.recruiter.application

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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
import uk.me.michaellloydlee.recruiter.fixtures.CHAT_ANSWER
import uk.me.michaellloydlee.recruiter.fixtures.JSON
import uk.me.michaellloydlee.recruiter.fixtures.decisionQuestions
import uk.me.michaellloydlee.recruiter.fixtures.idOf
import uk.me.michaellloydlee.recruiter.fixtures.jsonHeaders
import uk.me.michaellloydlee.recruiter.fixtures.message
import uk.me.michaellloydlee.recruiter.fixtures.messageList
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
import uk.me.michaellloydlee.recruiter.fixtures.decisionAnswer as decisionResponse

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

internal const val DECISION_ANSWER: String =
    """{"model":"clef:cloud","answers":{},"usage":{"input_tokens":10,"output_tokens":1}}"""

private val MESSAGE_LIST: String = messageList("first", "second")

internal fun gmailEngine(
    listing: Boolean = false,
    labels: List<String> = listOf("INBOX"),
    failModifyFor: String? = null,
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
                if (
                    failModifyFor != null &&
                    request.url.encodedPath.endsWith("/users/me/messages/$failModifyFor/modify")
                ) {
                    respond(
                        content = "boom",
                        status = HttpStatusCode.InternalServerError,
                        headers = jsonHeaders(),
                    )
                } else {
                    respond(
                        content = message(idOf(request), listing, labels),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }
    }

/**
 * The model answers the recruiter question and the four criteria of the
 * evaluation.
 *
 * `firstAnswer` is served to the first decision-model call of the run instead
 * of the usual answer, `firstChatAnswer` to the first chat-model call, a test
 * that needs a model which answers with something that is not the JSON the
 * client reads uses them. `failOnDecisionCall` and `failOnChatCall` are the
 * numbers of a decision or chat call that throws instead of answering, a test
 * that needs a mail whose processing dies uses them.
 */
internal fun modelEngine(
    officeDays: String = "matches",
    answered: Boolean = true,
    chatAnswers: Boolean = true,
    jdMatches: String = "matches",
    confidenceOfTheProfile: Double = 0.9,
    firstAnswer: String? = null,
    firstChatAnswer: String? = null,
    failOnDecisionCall: Int? = null,
    failOnChatCall: Int? = null,
): MockEngine {
    var served = 0
    var chatServed = 0
    return MockEngine { request ->
        if (request.url.encodedPath.endsWith("/api/chat")) {
            chatServed++
            respondToChat(chatServed, chatAnswers, firstChatAnswer, failOnChatCall)
        } else {
            served++
            respondToDecision(
                request,
                served,
                DecisionFixture(
                    answered,
                    jdMatches,
                    officeDays,
                    confidenceOfTheProfile,
                    firstAnswer,
                    failOnDecisionCall,
                ),
            )
        }
    }
}

private suspend fun MockRequestHandleScope.respondToChat(
    chatServed: Int,
    chatAnswers: Boolean,
    firstChatAnswer: String?,
    failOnChatCall: Int?,
): HttpResponseData =
    respond(
        content =
            if (failOnChatCall == chatServed) {
                error("The model engine blew up")
            } else if (firstChatAnswer != null && chatServed == 1) {
                firstChatAnswer
            } else {
                CHAT_ANSWER
            },
        status = if (chatAnswers) HttpStatusCode.OK else HttpStatusCode.NotFound,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

private class DecisionFixture(
    val answered: Boolean,
    val jdMatches: String,
    val officeDays: String,
    val confidenceOfTheProfile: Double,
    val firstAnswer: String?,
    val failOnDecisionCall: Int?,
)

private suspend fun MockRequestHandleScope.respondToDecision(
    request: HttpRequestData,
    served: Int,
    fixture: DecisionFixture,
): HttpResponseData {
    if (fixture.failOnDecisionCall == served) {
        error("The model engine blew up")
    }
    if (fixture.firstAnswer != null && served == 1) {
        return respond(
            content = fixture.firstAnswer,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
    val questions = decisionQuestions((request.body as TextContent).text)
    val answer =
        if (questions.containsKey("recruiter")) {
            decisionResponse(questions) { "from a recruiter" to 0.9 }
        } else if (fixture.answered) {
            decisionResponse(questions) { name ->
                val choice =
                    when (name) {
                        "jd_matches" -> fixture.jdMatches
                        "office_days" -> fixture.officeDays
                        else -> "does not match"
                    }
                val confidence = if (name == "jd_matches") fixture.confidenceOfTheProfile else 0.9
                choice to confidence
            }
        } else {
            DECISION_ANSWER
        }
    return respond(
        content = answer,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
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

private fun token(): OAuth2Token =
    OAuth2Token(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Instant.now().plusSeconds(600),
    )
