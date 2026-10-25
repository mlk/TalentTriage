package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GmailMessagesApiTest {
    @Test
    fun listsTheUnprocessedMessagesOfEveryPage() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.parameters["pageToken"] == null) {
                        respond(
                            content =
                                """{"messages":[{"id":"first","threadId":"thread-1"}],"nextPageToken":"page-2"}""",
                            status = HttpStatusCode.OK,
                        )
                    } else {
                        respond(
                            content = """{"messages":[{"id":"second","threadId":"thread-2"}]}""",
                            status = HttpStatusCode.OK,
                        )
                    }
                }

            val summaries = api(engine).listUnprocessed()

            assertEquals(listOf("first", "second"), summaries.map { it.id })
            assertEquals(listOf("thread-1", "thread-2"), summaries.map { it.threadId })
        }

    @Test
    fun searchesTheInboxMessagesWithoutAStateLabel() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"messages":[]}""", status = HttpStatusCode.OK)
                }

            api(engine).listUnprocessed()

            val request = engine.requestHistory.single()
            assertEquals(RecruiterLabel.UNPROCESSED_QUERY, request.url.parameters["q"])
            assertEquals("100", request.url.parameters["maxResults"])
            assertEquals("/v1/users/me/messages", request.url.encodedPath)
            assertTrue(
                request.url.toString().startsWith("https://gmail.example/v1/users/me/messages?"),
                "unexpected url: ${request.url}",
            )
        }

    @Test
    fun fetchesTheMessageWithItsContent() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "id": "message-id",
                              "threadId": "thread-id",
                              "labelIds": ["INBOX", "UNREAD"],
                              "snippet": "the snippet",
                              "payload": {
                                "mimeType": "text/plain",
                                "headers": [
                                  {"name": "From", "value": "Recruiter <recruiter@example.com>"},
                                  {"name": "Subject", "value": "Your CV"}
                                ],
                                "body": {"data": "SGVsbG8gcmVjcnVpdGVyIQ=="}
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                    )
                }

            val message = api(engine).getMessage("message-id")

            assertEquals("message-id", message.id)
            assertEquals("thread-id", message.threadId)
            assertEquals("Recruiter <recruiter@example.com>", message.sender)
            assertEquals("Your CV", message.subject)
            assertEquals("Hello recruiter!", message.body)
            assertEquals(listOf("INBOX", "UNREAD"), message.labelIds)
        }

    @Test
    fun readsTheTextPartsOfAMultipartMessage() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "id": "message-id",
                              "payload": {
                                "mimeType": "multipart/alternative",
                                "headers": [{"name": "Subject", "value": "Offer"}],
                                "parts": [
                                  {"mimeType": "text/plain", "body": {"data": "cGxhaW4gdGV4dA=="}},
                                  {"mimeType": "text/html", "body": {"data": "PGh0bWw+"}}
                                ]
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                    )
                }

            val message = api(engine).getMessage("message-id")

            assertEquals("Offer", message.subject)
            assertEquals("plain text", message.body)
        }

    @Test
    fun fallsBackToTheSnippetWhenTheBodyIsNotText() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"id":"message-id","snippet":"the snippet","payload":{"mimeType":"text/html"}}""",
                        status = HttpStatusCode.OK,
                    )
                }

            val message = api(engine).getMessage("message-id")

            assertEquals("the snippet", message.body)
        }

    @Test
    fun modifiesTheLabelsOfAMessage() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"id":"message-id"}""", status = HttpStatusCode.OK)
                }

            api(engine).modifyLabels("message-id", add = listOf("Label_1"), remove = listOf("INBOX"))

            val request = engine.requestHistory.single()
            assertEquals("POST", request.method.value)
            assertTrue(request.url.encodedPath.endsWith("/users/me/messages/message-id/modify"))
            val body = (request.body as TextContent).text
            assertTrue(body.contains("Label_1"))
            assertTrue(body.contains("INBOX"))
        }

    private fun api(engine: MockEngine): GmailMessagesApi {
        val client =
            GmailApiClient(
                httpClient = HttpClient(engine),
                tokens =
                    RefreshingAccessTokenProvider(
                        refresher = FakeRefresher(token("refreshed-access-token")),
                        tokenStore = FakeTokenStore(token("access-token")),
                        clock = Clock.fixed(NOW, ZoneOffset.UTC),
                    ),
                baseUrl = "https://gmail.example/v1",
            )
        return GmailMessagesApi(client)
    }

    private fun token(accessToken: String): OAuth2Token =
        OAuth2Token(
            accessToken = accessToken,
            refreshToken = "refresh-token",
            expiresAt = NOW.plusSeconds(600),
        )

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")
    }
}
