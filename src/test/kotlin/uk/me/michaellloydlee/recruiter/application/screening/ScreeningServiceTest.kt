package uk.me.michaellloydlee.recruiter.application.screening

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.screening.RecruiterHeuristics
import uk.me.michaellloydlee.recruiter.domain.screening.ScreeningDecision
import uk.me.michaellloydlee.recruiter.domain.screening.ScreeningStage
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiClient
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelStateManager
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelsApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Token
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.RefreshingAccessTokenProvider
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.TestOAuth2Client
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.temporaryTokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreeningServiceTest {
    @Test
    fun labelsAJobListingWithoutAskingTheModel() =
        runTest {
            val model = modelAnswering("from a recruiter")
            val labels = labelEngine()
            val service = service(model, labels)

            val outcome =
                service.screen(
                    message(
                        subject = "Lead Software Engineer job at LSEG - 1 connection",
                        sender = "LinkedIn Job Alerts <jobalerts-noreply@linkedin.com>",
                    ),
                )

            assertEquals(ScreeningDecision.JOB_LISTING, outcome.decision)
            assertEquals(ScreeningStage.HEURISTICS, outcome.stage)
            assertTrue(outcome.reason.contains("job listing"))
            assertEquals(0, model.requestHistory.size)
            assertTrue(labelBody(labels).contains(JOB_LISTING_LABEL_ID))
        }

    @Test
    fun filtersOutAMessageThatMatchesNoRule() =
        runTest {
            val model = modelAnswering("not from a recruiter")
            val labels = labelEngine()
            val service = service(model, labels)

            val outcome = service.screen(message(subject = "Your invoice", body = "The invoice is attached"))

            assertEquals(ScreeningDecision.NOT_RECRUITER, outcome.decision)
            assertEquals(ScreeningStage.HEURISTICS, outcome.stage)
            assertTrue(outcome.reason.contains("recruiter rules"))
            assertEquals(0, model.requestHistory.size)
            assertTrue(labels.requestHistory.isEmpty())
        }

    @Test
    fun filtersOutAMessageTheModelDoesNotSeeAsRecruiterMail() =
        runTest {
            val model = modelAnswering("not from a recruiter")
            val labels = labelEngine()
            val service = service(model, labels)

            val outcome = service.screen(message(subject = "Your CV", body = "We are looking for a developer"))

            assertEquals(ScreeningDecision.NOT_RECRUITER, outcome.decision)
            assertEquals(ScreeningStage.LOCAL_MODEL, outcome.stage)
            assertTrue(outcome.reason.contains("does not see a recruiter mail"))
            assertEquals(2, model.requestHistory.size)
            assertTrue(labels.requestHistory.isEmpty())
        }

    @Test
    fun keepsAMessageTheModelSeesAsRecruiterMail() =
        runTest {
            val model = modelAnswering("from a recruiter")
            val labels = labelEngine()
            val service = service(model, labels)

            val outcome = service.screen(message(subject = "Your CV", body = "We are looking for a developer"))

            assertEquals(ScreeningDecision.RECRUITER, outcome.decision)
            assertTrue(outcome.isRecruiter)
            assertTrue(outcome.reason.contains("sees a recruiter mail"))
            assertEquals(1, model.requestHistory.size)
            assertEquals(0, labels.requestHistory.size)
        }

    @Test
    fun leavesAMessageAloneWhenTheModelCannotBeAsked() =
        runTest {
            val model =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }
            val labels = labelEngine()
            val service = service(model, labels)

            val outcome = service.screen(message(subject = "Your CV", body = "We are looking for a developer"))

            assertEquals(ScreeningDecision.UNKNOWN, outcome.decision)
            assertEquals(ScreeningStage.LOCAL_MODEL, outcome.stage)
            assertTrue(outcome.reason.contains("model not found"))
            assertEquals(0, labels.requestHistory.size)
        }

    @Test
    fun asksTheModelWithTheSubjectAndTheBody() =
        runTest {
            val model = modelAnswering("from a recruiter")

            service(model, labelEngine())
                .screen(message(subject = "Your CV", body = "We are looking for a developer in Berlin"))

            val body = (model.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("Your CV"))
            assertTrue(body.contains("We are looking for a developer in Berlin"))
        }

    private suspend fun service(
        model: MockEngine,
        labels: MockEngine,
    ): ScreeningService {
        val client =
            OllamaDecisionClient(
                httpClient =
                    HttpClient(model) {
                        install(HttpTimeout)
                        install(ContentNegotiation) { json(JSON) }
                    },
                timeoutMillis = 30_000,
            )
        val api =
            GmailApiClient(
                httpClient = HttpClient(labels) { install(ContentNegotiation) { json(JSON) } },
                tokens =
                    RefreshingAccessTokenProvider(
                        refresher = TestOAuth2Client().client,
                        tokenStore = temporaryTokenStore(token()),
                    ),
                baseUrl = "https://gmail.example/v1",
            )
        val messages = GmailMessagesApi(api)
        return ScreeningService(
            heuristics = RecruiterHeuristics(),
            verifier = DecisionModelVerifier(client),
            stateManager = GmailLabelStateManager(messages, GmailLabelsApi(api)),
        )
    }

    private fun modelAnswering(answer: String): MockEngine =
        MockEngine {
            respond(
                content =
                    """
                    {"model":"clef:cloud","answers":{
                    "recruiter":{"type":"choice","choice":"$answer","confidence":0.9},
                    "job_listing":{"type":"choice","choice":"not a job listing","confidence":0.9}},
                    "usage":{"input_tokens":10,"output_tokens":1}}
                    """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

    private fun labelEngine(): MockEngine =
        MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/users/me/labels") && request.method.value == "GET" -> {
                    respond(
                        content = """{"labels":[{"id":"$JOB_LISTING_LABEL_ID","name":"Recruiters/JobListing"}]}""",
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

                else -> {
                    respond(
                        content = """{"id":"message-id"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }

    private fun idOf(label: String): String = "Label_${label.substringAfterLast('/').lowercase()}"

    private fun labelBody(engine: MockEngine): String = (engine.requestHistory.last().body as TextContent).text

    private fun message(
        subject: String = "",
        body: String = "",
        sender: String = "someone@example.com",
    ): EmailMessage =
        EmailMessage(
            id = "message-id",
            threadId = "thread-id",
            sender = sender,
            subject = subject,
            body = body,
            labelIds = listOf("INBOX"),
        )

    private fun token(): OAuth2Token =
        OAuth2Token(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Instant.now().plusSeconds(600),
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private const val JOB_LISTING_LABEL_ID = "Label_joblisting"

        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
