package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailDraft
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GmailDraftsApiTest {
    @Test
    fun writesTheDraftIntoTheMailbox() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"id":"draft-id"}""", status = HttpStatusCode.OK)
                }

            val id = api(engine).createDraft(draft())

            assertEquals("draft-id", id)
            val request = engine.requestHistory.single()
            assertEquals("POST", request.method.value)
            assertEquals("/v1/users/me/drafts", request.url.encodedPath)
        }

    @Test
    fun sendsTheMailOfTheDraftAsARawMessage() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"id":"draft-id"}""", status = HttpStatusCode.OK)
                }

            api(engine).createDraft(draft())

            val body = (engine.requestHistory.single().body as TextContent).text
            val message = Json.parseToJsonElement(body).jsonObject["message"]!!.jsonObject
            val raw = message["raw"]!!.jsonPrimitive.content
            assertEquals(
                "To: recruiter@example.com\r\n" +
                    "Subject: Re: Your CV\r\n" +
                    "In-Reply-To: <message-id>\r\n" +
                    "References: <message-id>\r\n" +
                    "Content-Type: text/plain; charset=\"UTF-8\"\r\n" +
                    "\r\n" +
                    "the reply of the model",
                String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8),
            )
        }

    @Test
    fun putsTheDraftIntoTheThreadOfTheMail() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"id":"draft-id"}""", status = HttpStatusCode.OK)
                }

            api(engine).createDraft(draft())

            val body = (engine.requestHistory.single().body as TextContent).text
            val message = Json.parseToJsonElement(body).jsonObject["message"]!!.jsonObject
            assertEquals("thread-id", message["threadId"]!!.jsonPrimitive.content)
        }

    private suspend fun api(engine: MockEngine): GmailDraftsApi {
        val client =
            GmailApiClient(
                httpClient = HttpClient(engine),
                tokens =
                    RefreshingAccessTokenProvider(
                        refresher = TestOAuth2Client(clock = Clock.fixed(NOW, ZoneOffset.UTC)).client,
                        tokenStore = temporaryTokenStore(token("access-token")),
                        clock = Clock.fixed(NOW, ZoneOffset.UTC),
                    ),
                baseUrl = "https://gmail.example/v1",
            )
        return GmailDraftsApi(client)
    }

    private fun token(accessToken: String): OAuth2Token =
        OAuth2Token(
            accessToken = accessToken,
            refreshToken = "refresh-token",
            expiresAt = NOW.plusSeconds(600),
        )

    private fun draft(): EmailDraft =
        EmailDraft(
            to = "recruiter@example.com",
            subject = "Re: Your CV",
            body = "the reply of the model",
            inReplyTo = "message-id",
            threadId = "thread-id",
        )

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")
    }
}
