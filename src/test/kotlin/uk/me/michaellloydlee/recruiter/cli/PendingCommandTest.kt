package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.run.FileLastRunStore
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingCommandTest {
    @Test
    fun listsTheUnprocessedMessages() =
        runTest {
            val sink = StringBuilder()
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/messages")) {
                        respond(
                            content = """{"messages":[{"id":"message-id","threadId":"thread-id"}]}""",
                            status = HttpStatusCode.OK,
                        )
                    } else {
                        respond(
                            content =
                                """
                                {
                                  "id": "message-id",
                                  "threadId": "thread-id",
                                  "labelIds": ["INBOX"],
                                  "payload": {
                                    "mimeType": "text/plain",
                                    "headers": [{"name": "Subject", "value": "Your CV"}],
                                    "body": {"data": "SGVsbG8="}
                                  }
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                        )
                    }
                }

            val result =
                cli(sink) { HttpClient(engine) }
                    .test("pending --credentials ${credentialsFile()} --token ${tokenFile()}")

            assertEquals(0, result.statusCode)
            val records = records(sink)
            assertEquals("pending.listed", records.first().event)
            assertEquals("1", records.first().attributes["count"])
            assertEquals("message-id", records.last().attributes["id"])
            assertEquals("Your CV", records.last().attributes["subject"])
        }

    @Test
    fun showsOnlyTheRequestedNumberOfMessages() =
        runTest {
            val sink = StringBuilder()
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/messages")) {
                        respond(
                            content = LIST_OF_TWO_MESSAGES,
                            status = HttpStatusCode.OK,
                        )
                    } else {
                        respond(
                            content = """{"id":"message-id","payload":{"mimeType":"text/plain"}}""",
                            status = HttpStatusCode.OK,
                        )
                    }
                }

            val result =
                cli(sink) { HttpClient(engine) }
                    .test("pending --limit 1 --credentials ${credentialsFile()} --token ${tokenFile()}")

            assertEquals(0, result.statusCode)
            val records = records(sink)
            assertEquals("2", records.first().attributes["count"])
            assertEquals(2, records.size)
        }

    @Test
    fun looksBackToTheLastSuccessfulRun() =
        runTest {
            val sink = StringBuilder()
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/messages")) {
                        respond(
                            content = """{"messages":[{"id":"message-id","threadId":"thread-id"}]}""",
                            status = HttpStatusCode.OK,
                        )
                    } else {
                        respond(content = """{"id":"message-id"}""", status = HttpStatusCode.OK)
                    }
                }
            val token = tokenFile()
            FileLastRunStore.nextTo(token).save(LAST_RUN)

            cli(sink, Clock.fixed(NOW, ZoneOffset.UTC)) { HttpClient(engine) }
                .test("pending --credentials ${credentialsFile()} --token $token")

            val search =
                engine.requestHistory
                    .first { it.url.encodedPath.endsWith("/messages") }
                    .url.parameters["q"]
            assertTrue(search!!.contains("after:${LAST_RUN.epochSecond}"))
        }

    @Test
    fun failsOnAnInvalidWindow() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink).test(
                    "pending --since 3y --credentials ${credentialsFile()} " +
                        "--token ${tokenFile()}",
                )

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsWhenTheCredentialsAreMissing() =
        runTest {
            val sink = StringBuilder()

            val result = cli(sink).test("pending --credentials /does/not/exist/credentials.json")

            assertEquals(1, result.statusCode)
        }

    private fun cli(
        sink: StringBuilder,
        clock: Clock = Clock.systemUTC(),
        httpClientFactory: () -> HttpClient = { HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }) },
    ): TriageCli = TriageCli(StructuredLogger(sink), httpClientFactory, clock)

    private fun tokenFile(): Path {
        val file = createTempDirectory("token").resolve("token.json")
        val expiry = Instant.now().plusSeconds(3600)
        file.writeText(
            """{"access_token":"access-token","refresh_token":"refresh-token","expires_at":"$expiry"}""",
        )
        return file
    }

    private fun credentialsFile(): Path {
        val file = createTempDirectory("credentials").resolve("credentials.json")
        file.writeText("""{"client_id":"client-id","client_secret":"client-secret"}""")
        return file
    }

    private fun records(sink: StringBuilder): List<LogRecord> =
        sink
            .toString()
            .trim()
            .lines()
            .map { Json.decodeFromString<LogRecord>(it) }

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-30T19:00:00Z")

        private val LAST_RUN: Instant = Instant.parse("2026-10-30T18:00:00Z")

        private const val LIST_OF_TWO_MESSAGES =
            """{"messages":[{"id":"first","threadId":"thread-1"},{"id":"second","threadId":"thread-2"}]}"""
    }
}
