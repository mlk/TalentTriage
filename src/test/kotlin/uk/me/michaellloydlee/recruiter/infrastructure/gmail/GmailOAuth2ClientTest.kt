package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GmailOAuth2ClientTest {
    @Test
    fun buildsTheAuthorizationUrl() {
        val url =
            client().authorizationUrl(
                redirectUri = "http://localhost",
                scopes = listOf("scope-a", "scope-b"),
                state = "state-1",
            )
        val parameters = URLBuilder(url).parameters

        assertTrue(url.startsWith(GoogleClientCredentials.DEFAULT_AUTH_URI))
        assertEquals("client-id", parameters["client_id"])
        assertEquals("http://localhost", parameters["redirect_uri"])
        assertEquals("code", parameters["response_type"])
        assertEquals("scope-a scope-b", parameters["scope"])
        assertEquals("offline", parameters["access_type"])
        assertEquals("consent", parameters["prompt"])
        assertEquals("state-1", parameters["state"])
    }

    @Test
    fun exchangesTheAuthorizationCode() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = TOKEN_RESPONSE,
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

            val token = client(engine).exchangeAuthorizationCode("the-code", "http://localhost")

            assertEquals("access-token", token.accessToken)
            assertEquals("refresh-token", token.refreshToken)
            assertEquals("Bearer", token.tokenType)
            assertEquals("scope-a", token.scope)
            assertEquals(NOW.plusSeconds(3600), token.expiresAt)

            val request = engine.requestHistory.single()
            assertEquals(GoogleClientCredentials.DEFAULT_TOKEN_URI, request.url.toString())
            val form = (request.body as FormDataContent).formData
            assertEquals("authorization_code", form["grant_type"])
            assertEquals("the-code", form["code"])
            assertEquals("client-id", form["client_id"])
            assertEquals("client-secret", form["client_secret"])
            assertEquals("http://localhost", form["redirect_uri"])
        }

    @Test
    fun refreshesTheAccessToken() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"access_token":"new-access-token","expires_in":3600}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

            val token = client(engine).refreshAccessToken("refresh-token")

            assertEquals("new-access-token", token.accessToken)
            assertEquals("refresh-token", token.refreshToken)
            assertNull(token.scope)

            val form = (engine.requestHistory.single().body as FormDataContent).formData
            assertEquals("refresh_token", form["grant_type"])
            assertEquals("refresh-token", form["refresh_token"])
            assertEquals("client-id", form["client_id"])
        }

    @Test
    fun failsWhenTheTokenEndpointRejectsTheRequest() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":"invalid_grant","error_description":"Bad Request"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = jsonHeaders(),
                    )
                }

            val error =
                assertFailsWith<OAuth2Exception> {
                    client(engine).exchangeAuthorizationCode("the-code", "http://localhost")
                }

            assertEquals("invalid_grant", error.error)
            assertEquals("Bad Request", error.errorDescription)
            assertEquals(400, error.statusCode)
        }

    @Test
    fun failsWhenTheTokenEndpointAnswersWithGarbage() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = "<html>gateway error</html>",
                        status = HttpStatusCode.BadGateway,
                        headers = jsonHeaders(),
                    )
                }

            val error =
                assertFailsWith<OAuth2Exception> {
                    client(engine).exchangeAuthorizationCode("the-code", "http://localhost")
                }

            assertEquals("invalid_response", error.error)
        }

    @Test
    fun failsWhenTheTokenResponseHasNoAccessToken() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"expires_in":3600}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

            val error =
                assertFailsWith<OAuth2Exception> {
                    client(engine).exchangeAuthorizationCode("the-code", "http://localhost")
                }

            assertEquals("invalid_response", error.error)
        }

    private fun client(engine: MockEngine = MockEngine { respond("{}", HttpStatusCode.OK) }): GmailOAuth2Client =
        GmailOAuth2Client(
            httpClient = HttpClient(engine),
            credentials = CREDENTIALS,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")

        private const val TOKEN_RESPONSE =
            """{"access_token":"access-token","refresh_token":"refresh-token",""" +
                """"expires_in":3600,"token_type":"Bearer","scope":"scope-a"}"""

        private val CREDENTIALS =
            GoogleClientCredentials(
                clientId = "client-id",
                clientSecret = "client-secret",
            )
    }
}
