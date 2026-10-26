package uk.me.michaellloydlee.recruiter.application

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.application.screening.LocalLlmClassifier
import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.screening.RecruiterHeuristics
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FakeRefresher
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FakeTokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiClient
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelStateManager
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelsApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Token
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.RefreshingAccessTokenProvider
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessInboxServiceTest {
    @Test
    fun screensEveryUnprocessedMessage() =
        runTest {
            val gmail = gmailEngine()
            val model = modelAnswering("no")
            val service = service(gmail, model)

            val summary = service.run()

            assertEquals(2, summary.screened)
            assertEquals(0, summary.recruiters)
            assertEquals(2, summary.filteredOut)
            assertEquals(0, summary.unknown)
        }

    @Test
    fun countsTheRecruiterMails() =
        runTest {
            val gmail = gmailEngine()
            val model = modelAnswering("yes")
            val service = service(gmail, model)

            val summary = service.run()

            assertEquals(2, summary.screened)
            assertEquals(2, summary.recruiters)
            assertEquals(0, summary.filteredOut)
        }

    @Test
    fun countsTheMessagesItCouldNotDecide() =
        runTest {
            val gmail = gmailEngine()
            val model =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }
            val service = service(gmail, model)

            val summary = service.run()

            assertEquals(2, summary.screened)
            assertEquals(0, summary.recruiters)
            assertEquals(0, summary.filteredOut)
            assertEquals(2, summary.unknown)
        }

    @Test
    fun doesNothingWithoutUnprocessedMessages() =
        runTest {
            val gmail =
                MockEngine {
                    respond(content = """{"messages":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            val model = modelAnswering("no")
            val service = service(gmail, model)

            val summary = service.run()

            assertEquals(0, summary.screened)
            assertEquals(0, model.requestHistory.size)
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
        return ProcessInboxService(
            messages = messages,
            logger = StructuredLogger(StringBuilder()),
            screening =
                ScreeningService(
                    heuristics = RecruiterHeuristics(),
                    classifier =
                        LocalLlmClassifier(
                            OllamaChatClient(
                                httpClient =
                                    HttpClient(model) {
                                        install(HttpTimeout)
                                        install(ContentNegotiation) { json(JSON) }
                                    },
                            ),
                        ),
                    stateManager = GmailLabelStateManager(messages, GmailLabelsApi(api)),
                ),
        )
    }

    private fun gmailEngine(): MockEngine =
        MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/users/me/messages") -> {
                    respond(
                        content =
                            """{"messages":[{"id":"first","threadId":"thread-1"},""" +
                                """{"id":"second","threadId":"thread-2"}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

                request.url.encodedPath.endsWith("/users/me/labels") && request.method.value == "GET" -> {
                    respond(
                        content = """{"labels":[{"id":"Label_filteredout","name":"Recruiters/FilteredOut"}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

                request.url.encodedPath.endsWith("/users/me/labels") -> {
                    respond(
                        content = """{"id":"Label_filteredout","name":"Recruiters/FilteredOut"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

                else -> {
                    respond(
                        content =
                            """
                            {
                              "id": "first",
                              "threadId": "thread-1",
                              "payload": {
                                "mimeType": "text/plain",
                                "headers": [{"name": "Subject", "value": "Your CV"}],
                                "body": {"data": "WWUgbG9va2luZyBmb3IgYSBkZXZlbG9wZXI="}
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }

    private fun modelAnswering(answer: String): MockEngine =
        MockEngine {
            respond(
                content = """{"message":{"role":"assistant","content":"$answer"},"done":true}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

    private fun token(): OAuth2Token =
        OAuth2Token(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Instant.now().plusSeconds(600),
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
