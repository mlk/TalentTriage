package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessInboxCommandTest {
    @Test
    fun screensTheUnprocessedMessagesWithTheDecisionModel() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token ${tokenFile()}",
                    )

            assertEquals(0, result.statusCode)
            val records = records(sink)
            assertEquals("process-inbox.started", records.first().event)
            val completed = records.last()
            assertEquals("process-inbox.completed", completed.event)
            assertEquals("1", completed.attributes["screened"])
            assertEquals("1", completed.attributes["filtered_out"])
            assertTrue(engine.requestHistory.any { it.url.encodedPath.endsWith("/v1/systemone") })
        }

    @Test
    fun screensTheUnprocessedMessagesWithTheChatModel() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --recruiter-verification=llm " +
                            "--credentials ${credentialsFile()} " +
                            "--token ${tokenFile()}",
                    )

            assertEquals(0, result.statusCode)
            assertTrue(engine.requestHistory.any { it.url.encodedPath.endsWith("/api/chat") })
        }

    @Test
    fun failsOnAnUnknownVerification() =
        runTest {
            val sink = StringBuilder()

            val result = cli(sink).test("process-inbox --recruiter-verification=magic")

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsWhenTheCredentialsAreMissing() =
        runTest {
            val sink = StringBuilder()

            val result = cli(sink).test("process-inbox --credentials /does/not/exist/credentials.json")

            assertEquals(1, result.statusCode)
        }

    private fun engine(): MockEngine =
        MockEngine { request ->
            respond(
                content = contentFor(request),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

    private fun contentFor(request: HttpRequestData): String =
        when {
            request.url.encodedPath.endsWith("/v1/systemone") -> {
                DECISION_ANSWER
            }

            request.url.encodedPath.endsWith("/api/chat") -> {
                CHAT_ANSWER
            }

            request.url.encodedPath.endsWith("/users/me/messages") -> {
                MESSAGE_LIST
            }

            request.url.encodedPath.endsWith("/users/me/labels") -> {
                if (request.method.value == "GET") LABEL_LIST else LABEL
            }

            else -> {
                MESSAGE
            }
        }

    private fun mockClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(HttpTimeout)
            install(ContentNegotiation) { json(JSON) }
        }

    private fun cli(
        sink: StringBuilder,
        httpClientFactory: () -> HttpClient = { HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }) },
    ): TriageCli = TriageCli(StructuredLogger(sink), httpClientFactory)

    private fun credentialsFile(): Path {
        val file = createTempDirectory("credentials").resolve("credentials.json")
        file.writeText("""{"client_id":"client-id","client_secret":"client-secret"}""")
        return file
    }

    private fun tokenFile(): Path {
        val file = createTempDirectory("token").resolve("token.json")
        val expiry = Instant.now().plusSeconds(3600)
        file.writeText(
            """{"access_token":"access-token","refresh_token":"refresh-token","expires_at":"$expiry"}""",
        )
        return file
    }

    private fun records(sink: StringBuilder): List<LogRecord> =
        sink
            .toString()
            .trim()
            .lines()
            .map { Json.decodeFromString<LogRecord>(it) }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        private const val DECISION_ANSWER =
            """{"model":"clef:cloud","answers":{"recruiter":{"type":"choice",
            "choice":"not from a recruiter","probabilities":{"not from a recruiter":0.9},
            "confidence":0.9}},"usage":{"input_tokens":10,"output_tokens":1}}"""

        private const val CHAT_ANSWER =
            """{"message":{"role":"assistant","content":"no"},"done":true}"""

        private const val MESSAGE_LIST =
            """{"messages":[{"id":"message-id","threadId":"thread-id"}]}"""

        private const val LABEL_LIST =
            """{"labels":[{"id":"Label_filteredout","name":"Recruiters/FilteredOut"}]}"""

        private const val LABEL =
            """{"id":"Label_filteredout","name":"Recruiters/FilteredOut"}"""

        private val MESSAGE =
            """
            {
              "id": "message-id",
              "threadId": "thread-id",
              "payload": {
                "mimeType": "text/plain",
                "headers": [{"name": "Subject", "value": "Your CV"}],
                "body": {"data": "WWUgbG9va2luZyBmb3IgYSBkZXZlbG9wZXI="}
              }
            }
            """.trimIndent()
    }
}
