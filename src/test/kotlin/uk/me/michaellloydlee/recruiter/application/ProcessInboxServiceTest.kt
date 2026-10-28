package uk.me.michaellloydlee.recruiter.application

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
import uk.me.michaellloydlee.recruiter.application.evaluation.AttachmentTextReader
import uk.me.michaellloydlee.recruiter.application.evaluation.DecisionModelEvaluator
import uk.me.michaellloydlee.recruiter.application.evaluation.EvaluationService
import uk.me.michaellloydlee.recruiter.application.screening.DecisionModelVerifier
import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FakeRefresher
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FakeTokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiClient
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelStateManager
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelsApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Token
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.RefreshingAccessTokenProvider
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessInboxServiceTest {
    @Test
    fun screensEveryUnprocessedMessage() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run()

            assertEquals(2, summary.screened)
            assertEquals(2, summary.recruiters)
            assertEquals(0, summary.filteredOut)
            assertEquals(0, summary.unknown)
        }

    @Test
    fun countsTheStateOfTheRecruiterMails() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run()

            assertEquals(2, summary.screened)
            assertEquals(2, summary.recruiters)
            assertEquals(2, summary.worthFollowUp)
            assertEquals(0, summary.needsPayCheck)
            assertEquals(0, summary.trashed)
        }

    @Test
    fun countsTheMessagesItCouldNotDecide() =
        runTest {
            val gmail = gmailEngine()
            val model =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            val summary = service(gmail, model).run()

            assertEquals(2, summary.screened)
            assertEquals(0, summary.recruiters)
            assertEquals(0, summary.filteredOut)
            assertEquals(2, summary.unknown)
        }

    @Test
    fun labelsEveryMailItDecidedSoItIsNotScreenedAgain() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            service(gmail, model).run()

            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_worthfollowup"))
            }
        }

    @Test
    fun leavesTheMailItCouldNotDecideWithoutALabel() =
        runTest {
            val gmail = gmailEngine()
            val model =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            service(gmail, model).run()

            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertTrue(labels.isEmpty())
        }

    private fun service(
        gmail: MockEngine,
        model: MockEngine,
    ): ProcessInboxService {
        val api =
            GmailApiClient(
                httpClient = HttpClient(gmail) { install(ContentNegotiation) { json(JSON) } },
                tokens =
                    RefreshingAccessTokenProvider(
                        refresher = FakeRefresher(token()),
                        tokenStore = FakeTokenStore(token()),
                    ),
                baseUrl = "https://gmail.example/v1",
            )
        val messages = GmailMessagesApi(api)
        val stateManager = GmailLabelStateManager(messages, GmailLabelsApi(api))
        val modelClient =
            OllamaDecisionClient(
                httpClient =
                    HttpClient(model) {
                        install(HttpTimeout)
                        install(ContentNegotiation) { json(JSON) }
                    },
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
                    stateManager = stateManager,
                ),
            attachments = AttachmentTextReader(messages),
            requirements =
                JobRequirements(
                    minimumPay = null,
                    maximumOfficeDays = 2,
                    profile = "Senior Kotlin engineer",
                ),
            logger = StructuredLogger(StringBuilder()),
        )
    }

    /**
     * The model answers the recruiter question and the four criteria of the
     * evaluation.
     */
    private fun modelEngine(): MockEngine =
        MockEngine { request ->
            val body = (request.body as TextContent).text
            val questions = Json.parseToJsonElement(body).jsonObject["questions"]!!.jsonObject
            val answer =
                if (questions.containsKey("recruiter")) {
                    """"recruiter":{"type":"choice","choice":"from a recruiter","confidence":0.9}"""
                } else {
                    questions.keys.joinToString(",") { name ->
                        val matches = name == "jd_matches" || name == "office_days_matches"
                        val choice = if (matches) "matches" else "does not match"
                        """"$name":{"type":"choice","choice":"$choice","confidence":0.9}"""
                    }
                }
            respond(
                content = DECISION_ANSWER.replace("{}", "{$answer}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun gmailEngine(): MockEngine =
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
                    respond(
                        content = """{"id":"Label_worthfollowup","name":"Recruiters/WorthFollowUp"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

                else -> {
                    respond(
                        content = message(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }

    private fun message(): String {
        val text =
            """
            {
              "id": "first",
              "threadId": "thread-1",
              "labelIds": ["INBOX"],
              "payload": {
                "mimeType": "text/plain",
                "headers": [
                  {"name": "From", "value": "recruiter@example.com"},
                  {"name": "Subject", "value": "Your CV"}
                ],
                "body": {"data": "WWUgbG9va2luZyBmb3IgYSBkZXZlbG9wZXI="}
              }
            }
            """.trimIndent()
        return text
    }

    private fun token(): OAuth2Token =
        OAuth2Token(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Instant.now().plusSeconds(600),
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private const val DECISION_ANSWER =
            """{"model":"clef:cloud","answers":{},"usage":{"input_tokens":10,"output_tokens":1}}"""

        private const val MESSAGE_LIST =
            """{"messages":[{"id":"first","threadId":"thread-1"},{"id":"second","threadId":"thread-2"}]}"""

        private val JSON: Json = Json { ignoreUnknownKeys = true }
    }
}
