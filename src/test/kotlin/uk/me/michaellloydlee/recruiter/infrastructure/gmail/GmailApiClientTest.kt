package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GmailApiClientTest {
    @Test
    fun sendsTheBearerToken() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"id":"message-id"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

            val message = apiClient(engine).get("users/me/messages/message-id", TestMessage.serializer())

            assertEquals("message-id", message.id)
            val request = engine.requestHistory.single()
            assertEquals("https://gmail.example/v1/users/me/messages/message-id", request.url.toString())
            assertEquals("Bearer access-token", request.headers["Authorization"])
        }

    @Test
    fun refreshesTheTokenAndRetriesWhenTheApiRejectsIt() =
        runTest {
            var call = 0
            val engine =
                MockEngine {
                    call++
                    if (call == 1) {
                        respond("unauthorized", HttpStatusCode.Unauthorized)
                    } else {
                        respond(
                            content = """{"id":"message-id"}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders(),
                        )
                    }
                }
            val tokens = FakeAccessTokenProvider()

            val message = apiClient(engine, tokens).get("messages/message-id", TestMessage.serializer())

            assertEquals("message-id", message.id)
            assertEquals(2, engine.requestHistory.size)
            assertEquals("Bearer access-token", engine.requestHistory[0].headers["Authorization"])
            assertEquals("Bearer refreshed-token", engine.requestHistory[1].headers["Authorization"])
            assertEquals(1, tokens.refreshCount)
        }

    @Test
    fun failsWhenTheApiAnswersWithAnError() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":{"message":"not found"}}""",
                        status = HttpStatusCode.NotFound,
                        headers = jsonHeaders(),
                    )
                }

            val error =
                assertFailsWith<GmailApiException> {
                    apiClient(engine).get("messages/unknown", TestMessage.serializer())
                }

            assertEquals(404, error.statusCode)
            assertTrue(error.body.contains("not found"))
        }

    @Test
    fun postsTheJsonBody() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"id":"label-id"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }

            val label = apiClient(engine).post("labels", """{"name":"Recruiters/Trash"}""", TestMessage.serializer())

            assertEquals("label-id", label.id)
            val request = engine.requestHistory.single()
            assertEquals("POST", request.method.value)
            assertEquals(ContentType.Application.Json.toString(), request.body.contentType?.toString())
        }

    private fun apiClient(
        engine: MockEngine,
        tokens: AccessTokenProvider = FakeAccessTokenProvider(),
    ): GmailApiClient =
        GmailApiClient(
            httpClient = HttpClient(engine),
            tokens = tokens,
            baseUrl = "https://gmail.example/v1",
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Serializable
    data class TestMessage(
        val id: String,
    )

    private class FakeAccessTokenProvider : AccessTokenProvider {
        var refreshCount: Int = 0
            private set

        override suspend fun accessToken(): String = "access-token"

        override suspend fun refresh(): String {
            refreshCount++
            return "refreshed-token"
        }
    }
}
