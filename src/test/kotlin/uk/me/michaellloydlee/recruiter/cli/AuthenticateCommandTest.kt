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
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticateCommandTest {
    @Test
    fun printsTheAuthorizationUrlWhenNoCodeIsGiven() =
        runTest {
            val sink = StringBuilder()
            val credentials = credentialsFile()

            val result = cli(sink).test("authenticate --credentials $credentials")

            assertEquals(0, result.statusCode)
            assertTrue(result.stderr.contains("accounts.google.com"))
            val record = record(sink)
            assertEquals("authenticate.authorisation_required", record.event)
            assertTrue(record.attributes["url"]!!.contains("accounts.google.com"))
        }

    @Test
    fun exchangesTheCodeAndStoresTheToken() =
        runTest {
            val sink = StringBuilder()
            val credentials = credentialsFile()
            val tokenFile = createTempDirectory("token").resolve("token.json")
            val engine =
                MockEngine {
                    respond(
                        content =
                            """{"access_token":"access-token",""" +
                                """"refresh_token":"refresh-token","expires_in":3600}""",
                        status = HttpStatusCode.OK,
                    )
                }

            val result =
                cli(sink) { HttpClient(engine) }
                    .test(
                        "authenticate the-code --credentials $credentials " +
                            "--token $tokenFile",
                    )

            assertEquals(0, result.statusCode)
            assertEquals("authenticate.completed", record(sink).event)
            assertTrue(tokenFile.readText().contains("access-token"))
        }

    @Test
    fun failsWhenTheCredentialsAreMissing() =
        runTest {
            val sink = StringBuilder()

            val result = cli(sink).test("authenticate --credentials /does/not/exist/credentials.json")

            assertEquals(1, result.statusCode)
        }

    @Test
    fun failsWhenTheTokenEndpointRejectsTheCode() =
        runTest {
            val sink = StringBuilder()
            val credentials = credentialsFile()
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":"invalid_grant","error_description":"Bad Request"}""",
                        status = HttpStatusCode.BadRequest,
                    )
                }

            val result =
                cli(sink) { HttpClient(engine) }
                    .test("authenticate the-code --credentials $credentials")

            assertEquals(1, result.statusCode)
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

    private fun record(sink: StringBuilder): LogRecord =
        Json.decodeFromString(
            sink
                .toString()
                .trim()
                .lines()
                .last(),
        )
}
