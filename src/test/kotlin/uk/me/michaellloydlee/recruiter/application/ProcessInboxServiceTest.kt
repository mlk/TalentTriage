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
import kotlinx.serialization.json.jsonPrimitive
import uk.me.michaellloydlee.recruiter.application.evaluation.AttachmentTextReader
import uk.me.michaellloydlee.recruiter.application.evaluation.DecisionModelEvaluator
import uk.me.michaellloydlee.recruiter.application.evaluation.EvaluationService
import uk.me.michaellloydlee.recruiter.application.screening.DecisionModelVerifier
import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiClient
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelStateManager
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelsApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Token
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.RefreshingAccessTokenProvider
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.TestOAuth2Client
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.temporaryTokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
    fun countsAJobListingAndDoesNotEvaluateIt() =
        runTest {
            val gmail = gmailEngine(listing = true)
            val model = modelEngine()

            val summary = service(gmail, model).run()

            assertEquals(2, summary.screened)
            assertEquals(2, summary.jobListings)
            assertEquals(0, summary.recruiters)
            assertEquals(0, model.requestHistory.size)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_joblisting"))
            }
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
            assertEquals(0, summary.needsMoreInformation)
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
    fun tagsAMailWithUnclearOfficeDaysForMoreInformation() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")

            val summary = service(gmail, model).run()

            assertEquals(2, summary.needsMoreInformation)
            assertEquals(0, summary.worthFollowUp)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_needsmoreinformation"))
            }
        }

    @Test
    fun theLogSaysWhatIsMissing() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")
            val log = StringBuilder()

            service(gmail, model, log).run()

            assertTrue(log.toString().contains("does not say how many days of office"))
        }

    @Test
    fun tagsAMailTheModelCouldNotEvaluateForMoreInformation() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(answered = false)

            val summary = service(gmail, model).run()

            assertEquals(2, summary.screened)
            assertEquals(2, summary.recruiters)
            assertEquals(2, summary.needsMoreInformation)
            assertEquals(0, summary.unknown)
            val labels = gmail.requestHistory.filter { it.url.encodedPath.endsWith("/modify") }
            assertEquals(2, labels.size)
            labels.forEach { label ->
                val body = (label.body as TextContent).text
                assertTrue(body.contains("Label_needsmoreinformation"))
            }
        }

    @Test
    fun theLogSaysThatTheModelCouldNotEvaluateTheJobDescription() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(answered = false)
            val log = StringBuilder()

            service(gmail, model, log).run()

            assertTrue(log.toString().contains("could not evaluate the job description"))
        }

    @Test
    fun looksAtTheMessagesOfTheWindowOnly() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            service(gmail, model, since = NOW.minusSeconds(6 * 3600)).run()

            val search =
                gmail.requestHistory
                    .first { it.url.encodedPath.endsWith("/users/me/messages") }
                    .url.parameters["q"]
            assertTrue(search!!.contains("after:${NOW.minusSeconds(6 * 3600).epochSecond}"))
        }

    @Test
    fun theSummaryCarriesTheMomentTheRunLookedBackTo() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()
            val since = NOW.minusSeconds(6 * 3600)

            val summary = service(gmail, model, since = since).run()

            assertEquals(since, summary.since)
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

    private suspend fun service(
        gmail: MockEngine,
        model: MockEngine,
        log: StringBuilder = StringBuilder(),
        since: Instant = NOW.minusSeconds(86400),
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
            since = since,
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
            logger = StructuredLogger(log),
        )
    }

    /**
     * The model answers the recruiter question and the four criteria of the
     * evaluation.
     */
    private fun modelEngine(
        officeDays: String = "matches",
        answered: Boolean = true,
    ): MockEngine =
        MockEngine { request ->
            val body = (request.body as TextContent).text
            val questions = Json.parseToJsonElement(body).jsonObject["questions"]!!.jsonObject
            val answer =
                if (questions.containsKey("recruiter")) {
                    """"recruiter":{"type":"choice","choice":"from a recruiter","confidence":0.9}"""
                } else if (answered) {
                    questions.keys.joinToString(",") { name ->
                        val choice =
                            when (name) {
                                "jd_matches" -> "matches"
                                "office_days" -> officeDays
                                else -> "does not match"
                            }
                        """"$name":{"type":"choice","choice":"$choice","confidence":0.9}"""
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

    private fun idOf(label: String): String = "Label_${label.substringAfterLast('/').lowercase()}"

    private fun gmailEngine(listing: Boolean = false): MockEngine =
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

                else -> {
                    respond(
                        content = message(listing),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }

    private fun message(listing: Boolean = false): String {
        val sender =
            if (listing) {
                "LinkedIn Job Alerts &lt;jobalerts-noreply@linkedin.com&gt;"
            } else {
                "recruiter@example.com"
            }
        val text =
            """
            {
              "id": "first",
              "threadId": "thread-1",
              "labelIds": ["INBOX"],
              "payload": {
                "mimeType": "text/plain",
                "headers": [
                  {"name": "From", "value": "$sender"},
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
        private val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")

        private const val DECISION_ANSWER =
            """{"model":"clef:cloud","answers":{},"usage":{"input_tokens":10,"output_tokens":1}}"""

        private const val MESSAGE_LIST =
            """{"messages":[{"id":"first","threadId":"thread-1"},{"id":"second","threadId":"thread-2"}]}"""

        private val JSON: Json = Json { ignoreUnknownKeys = true }
    }
}
