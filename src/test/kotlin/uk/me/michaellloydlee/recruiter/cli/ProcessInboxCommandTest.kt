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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
import kotlin.test.assertNull
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
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()}",
                    )

            assertEquals(0, result.statusCode)
            val records = records(sink)
            assertEquals("process-inbox.started", records.first().event)
            val completed = records.last()
            assertEquals("process-inbox.completed", completed.event)
            assertEquals("1", completed.attributes["screened"])
            assertEquals("1", completed.attributes["recruiters"])
            assertEquals("1", completed.attributes["worth_follow_up"])
            assertEquals("0", completed.attributes["needs_more_information"])
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
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()}",
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
    fun failsWhenTheRequirementsAreMissing() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink).test(
                    "process-inbox --credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements /does/not/exist/requirements.json",
                )

            assertEquals(1, result.statusCode)
        }

    @Test
    fun usesTheEndpointOfTheModelCalls() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()} " +
                            "--recruiter-verification-endpoint http://screening.example:11434 " +
                            "--job-description-evaluation-endpoint http://evaluation.example:8000",
                    )

            assertEquals(0, result.statusCode)
            val endpoints = engine.requestHistory.map { it.url.host }.distinct()
            assertTrue(endpoints.contains("screening.example"))
            assertTrue(endpoints.contains("evaluation.example"))
        }

    @Test
    fun failsOnAnEndpointWithoutAScheme() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink).test(
                    "process-inbox --recruiter-verification-endpoint localhost:11434 " +
                        "--credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()}",
                )

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsOnATimeoutThatIsNotANumber() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink).test(
                    "process-inbox --recruiter-verification-timeout soon " +
                        "--credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()}",
                )

            assertEquals(1, result.statusCode)
        }

    @Test
    fun looksAtTheMessagesOfTheWindowOfTheRun() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --since PT6H " +
                            "--credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()}",
                    )

            assertEquals(0, result.statusCode)
            val search =
                engine.requestHistory
                    .first { it.url.encodedPath.endsWith("/users/me/messages") }
                    .url.parameters["q"]
            assertTrue(search!!.contains("after:"))
        }

    @Test
    fun theWindowOfTheRunIsInTheLog() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --since PT6H " +
                            "--credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()}",
                    )

            assertEquals(0, result.statusCode)
            val completed = records(sink).last()
            assertEquals("PT6H", completed.attributes["window"])
        }

    @Test
    fun looksBackToTheLastSuccessfulRun() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()
            val token = tokenFile()
            FileLastRunStore.nextTo(token).save(LAST_RUN)

            val result =
                cli(sink, Clock.fixed(NOW, ZoneOffset.UTC)) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token $token --requirements ${requirementsFile()}",
                    )

            assertEquals(0, result.statusCode)
            val search =
                engine.requestHistory
                    .first { it.url.encodedPath.endsWith("/users/me/messages") }
                    .url.parameters["q"]
            assertTrue(search!!.contains("after:${LAST_RUN.epochSecond}"))
            val completed = records(sink).last()
            assertEquals("PT1H", completed.attributes["window"])
            assertEquals(LAST_RUN.toString(), completed.attributes["since"])
        }

    @Test
    fun theWindowOfTheCommandLineWinsOverTheLastRun() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()
            val token = tokenFile()
            FileLastRunStore.nextTo(token).save(LAST_RUN)

            val result =
                cli(sink, Clock.fixed(NOW, ZoneOffset.UTC)) { mockClient(engine) }
                    .test(
                        "process-inbox --since PT6H " +
                            "--credentials ${credentialsFile()} " +
                            "--token $token --requirements ${requirementsFile()}",
                    )

            assertEquals(0, result.statusCode)
            assertEquals("PT6H", records(sink).last().attributes["window"])
        }

    @Test
    fun remembersTheMomentOfTheRun() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()
            val token = tokenFile()

            val result =
                cli(sink, Clock.fixed(NOW, ZoneOffset.UTC)) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token $token --requirements ${requirementsFile()}",
                    )

            assertEquals(0, result.statusCode)
            assertEquals(NOW, FileLastRunStore.nextTo(token).load())
        }

    @Test
    fun doesNotRememberTheMomentOfAFailedRun() =
        runTest {
            val sink = StringBuilder()
            val token = tokenFile()
            val engine = MockEngine { respond(content = "boom", status = HttpStatusCode.InternalServerError) }

            val result =
                cli(sink, Clock.fixed(NOW, ZoneOffset.UTC)) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token $token --requirements ${requirementsFile()}",
                    )

            assertEquals(1, result.statusCode)
            assertNull(FileLastRunStore.nextTo(token).load())
        }

    @Test
    fun failsOnAWindowThatIsNotADuration() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink).test(
                    "process-inbox --since 3y " +
                        "--credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()}",
                )

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsOnAWindowOfZero() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink).test(
                    "process-inbox --since PT0S " +
                        "--credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()}",
                )

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
                decisionAnswer(request)
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

    /**
     * The answer of the decision model: the recruiter question of the screening
     * and the criteria of the evaluation are answered differently.
     */
    private fun decisionAnswer(request: HttpRequestData): String {
        val body = (request.body as TextContent).text
        val questions = Json.parseToJsonElement(body).jsonObject["questions"]!!.jsonObject
        val answer =
            questions.keys.joinToString(",") { name ->
                val choice =
                    when (name) {
                        "recruiter" -> "from a recruiter"
                        "jd_matches", "office_days" -> "matches"
                        else -> "does not match"
                    }
                """"$name":{"type":"choice","choice":"$choice","confidence":0.9}"""
            }
        return """{"model":"clef:cloud","answers":{$answer},"usage":{"input_tokens":10,"output_tokens":1}}"""
    }

    private fun mockClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(HttpTimeout)
            install(ContentNegotiation) { json(JSON) }
        }

    private fun cli(
        sink: StringBuilder,
        clock: Clock = Clock.systemUTC(),
        httpClientFactory: () -> HttpClient = { HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }) },
    ): TriageCli = TriageCli(StructuredLogger(sink), httpClientFactory, clock)

    private fun credentialsFile(): Path {
        val file = createTempDirectory("credentials").resolve("credentials.json")
        file.writeText("""{"client_id":"client-id","client_secret":"client-secret"}""")
        return file
    }

    private fun requirementsFile(): Path {
        val file = createTempDirectory("requirements").resolve("requirements.json")
        file.writeText(
            """{"maximum_office_days": 2, "profile": "Senior Kotlin engineer"}""",
        )
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
        private val NOW: Instant = Instant.parse("2026-10-30T19:00:00Z")

        private val LAST_RUN: Instant = Instant.parse("2026-10-30T18:00:00Z")

        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        private const val CHAT_ANSWER =
            """{"message":{"role":"assistant","content":"no"},"done":true}"""

        private const val MESSAGE_LIST =
            """{"messages":[{"id":"message-id","threadId":"thread-id"}]}"""

        private const val LABEL_LIST =
            """{"labels":[{"id":"Label_joblisting","name":"Recruiters/JobListing"}]}"""

        private const val LABEL =
            """{"id":"Label_joblisting","name":"Recruiters/JobListing"}"""

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
