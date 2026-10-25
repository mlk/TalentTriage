package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class GmailHttpClientFactoryTest {
    @Test
    fun createsAClientThatDeserialisesTheJsonResponses() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"id":"message-id","unknown":"ignored"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = GmailHttpClientFactory.create(engine = engine)

            val message: TestMessage = client.get("https://gmail.example/v1/messages/message-id").body()

            assertEquals("message-id", message.id)
        }

    @Test
    fun createsAClientThatDoesNotFailOnErrorResponses() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":{"message":"not found"}}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = GmailHttpClientFactory.create(engine = engine)

            val response = client.get("https://gmail.example/v1/messages/unknown")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Serializable
    data class TestMessage(
        val id: String,
    )
}
