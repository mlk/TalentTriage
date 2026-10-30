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
import uk.me.michaellloydlee.recruiter.infrastructure.parser.TestDocuments
import uk.me.michaellloydlee.recruiter.infrastructure.requirements.RequirementsFile
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequirementsCommandTest {
    @Test
    fun writesTheRequirementsOfTheJobSeeker() =
        runTest {
            val sink = StringBuilder()
            val target = targetFile()

            val result = cli(sink).test("requirements ${cvFile()} --requirements $target")

            assertEquals(0, result.statusCode)
            val requirements = RequirementsFile.load(target)
            assertEquals("Senior Kotlin engineer of the payment team", requirements.profile)
            assertEquals(2, requirements.maximumOfficeDays)
            assertEquals("GBP", requirements.minimumPay?.currency)
        }

    @Test
    fun theDaysOfOfficeComeFromTheCommandLine() =
        runTest {
            val sink = StringBuilder()
            val target = targetFile()

            val result = cli(sink).test("requirements ${cvFile()} --requirements $target --max-office-days 4")

            assertEquals(0, result.statusCode)
            assertEquals(4, RequirementsFile.load(target).maximumOfficeDays)
        }

    @Test
    fun printsWhatItSuggests() =
        runTest {
            val sink = StringBuilder()

            val result = cli(sink).test("requirements ${cvFile()} --requirements ${targetFile()}")

            assertEquals(0, result.statusCode)
            assertTrue(result.stderr.contains("Senior Kotlin engineer of the payment team"))
            assertTrue(result.stderr.contains("maximum office days: 2"))
        }

    @Test
    fun theRunIsInTheLog() =
        runTest {
            val sink = StringBuilder()
            val target = targetFile()

            cli(sink).test("requirements ${cvFile()} --requirements $target")

            val records = records(sink)
            assertEquals("requirements.started", records.first().event)
            val completed = records.last()
            assertEquals("requirements.completed", completed.event)
            assertEquals(target.toString(), completed.attributes["file"])
            assertEquals("2", completed.attributes["maximum_office_days"])
        }

    @Test
    fun doesNotOverwriteTheFileWithoutForce() =
        runTest {
            val sink = StringBuilder()
            val target = targetFile()
            target.writeText("""{"maximum_office_days": 2, "profile": "The old profile"}""")

            val result = cli(sink).test("requirements ${cvFile()} --requirements $target")

            assertEquals(1, result.statusCode)
            assertTrue(RequirementsFile.load(target).profile == "The old profile")
        }

    @Test
    fun overwritesTheFileWithForce() =
        runTest {
            val sink = StringBuilder()
            val target = targetFile()
            target.writeText("""{"maximum_office_days": 2, "profile": "The old profile"}""")

            val result = cli(sink).test("requirements ${cvFile()} --requirements $target --force")

            assertEquals(0, result.statusCode)
            assertTrue(RequirementsFile.load(target).profile != "The old profile")
        }

    @Test
    fun failsWhenTheCvIsMissing() =
        runTest {
            val sink = StringBuilder()

            val result = cli(sink).test("requirements /does/not/exist/cv.pdf")

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsWhenTheCvIsNoDocument() =
        runTest {
            val sink = StringBuilder()
            val cv = createTempDirectory("cv").resolve("cv.txt")
            cv.writeText("Michael Lloyd Lee is a senior Kotlin engineer.")

            val result = cli(sink).test("requirements $cv")

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsWhenTheModelCannotBeReached() =
        runTest {
            val sink = StringBuilder()
            val engine = MockEngine { throw java.io.IOException("the network is down") }

            val result = cli(sink, engine).test("requirements ${cvFile()} --requirements ${targetFile()}")

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsOnAnEndpointWithoutAScheme() =
        runTest {
            val sink = StringBuilder()

            val result = cli(sink).test("requirements ${cvFile()} --endpoint localhost:11434")

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsOnANegativeNumberOfDaysOfOffice() =
        runTest {
            val sink = StringBuilder()

            val result = cli(sink).test("requirements ${cvFile()} --max-office-days -1")

            assertEquals(1, result.statusCode)
        }

    private fun cli(
        sink: StringBuilder,
        engine: MockEngine = engine(),
    ): TriageCli =
        TriageCli(
            StructuredLogger(sink),
            {
                HttpClient(engine) {
                    install(HttpTimeout)
                    install(ContentNegotiation) { json(JSON) }
                }
            },
        )

    private fun engine(): MockEngine =
        MockEngine { request ->
            respond(answerFor(request), HttpStatusCode.OK, jsonHeaders())
        }

    private fun answerFor(request: HttpRequestData): String =
        when {
            request.url.encodedPath.endsWith("/api/chat") -> {
                """{"message":{"role":"assistant","content":"$PROFILE_ANSWER"},"done":true}"""
            }

            else -> {
                """
                {"model":"clef:cloud",
                 "answers":{"minimum_pay":{"type":"choice","choice":"2","confidence":0.9}},
                 "usage":{"input_tokens":10,"output_tokens":1}}
                """.trimIndent()
            }
        }

    private fun cvFile(): Path {
        val file = createTempDirectory("cv").resolve("cv.pdf")
        file.writeBytes(TestDocuments.pdfWith(listOf("Michael Lloyd Lee is a senior Kotlin engineer.")))
        return file
    }

    private fun targetFile(): Path = createTempDirectory("requirements").resolve("requirements.json")

    private fun records(sink: StringBuilder): List<LogRecord> =
        sink
            .toString()
            .trim()
            .lines()
            .map { Json.decodeFromString<LogRecord>(it) }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private const val PROFILE_ANSWER =
            """{\"profile\":\"Senior Kotlin engineer of the payment team\",""" +
                """\"role\":\"Senior Kotlin engineer\",\"region\":\"London, United Kingdom\"}"""

        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
