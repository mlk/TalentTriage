package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticateCommandTest {
    @Test
    fun opensTheBrowserAndWaitsForTheCode() =
        runTest {
            val sink = StringBuilder()
            val credentials = credentialsFile()
            val tokenFile = createTempDirectory("token").resolve("token.json")
            var opened = ""

            val result =
                cli(sink, openBrowser = { url ->
                    opened = url
                    redirectTo(url)
                }) { HttpClient(tokenEngine()) }
                    .test("authenticate --credentials $credentials --token $tokenFile")

            assertEquals(0, result.statusCode)
            assertTrue(opened.contains("accounts.google.com"))
            assertTrue(result.stderr.contains("accounts.google.com"))
            assertEquals("authenticate.completed", record(sink).event)
            assertTrue(tokenFile.readText().contains("access-token"))
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

    @Test
    fun failsWhenTheRedirectCarriesAnotherState() =
        runTest {
            val sink = StringBuilder()
            val credentials = credentialsFile()
            val tokenFile = createTempDirectory("token").resolve("token.json")

            val result =
                cli(sink, openBrowser = { url -> redirectTo(url, state = "another-state") }) {
                    HttpClient(tokenEngine())
                }.test("authenticate --credentials $credentials --token $tokenFile")

            assertEquals(1, result.statusCode)
            assertFalse(tokenFile.exists())
        }

    private fun tokenEngine(): MockEngine =
        MockEngine {
            respond(
                content = """{"access_token":"access-token","expires_in":3600}""",
                status = HttpStatusCode.OK,
            )
        }

    /**
     * The redirect of the browser to the local service: the browser of the job
     * seeker goes to Google and comes back with the code.
     */
    private suspend fun redirectTo(
        authorizationUrl: String,
        state: String? = null,
    ) {
        val parameters = Url(authorizationUrl).parameters
        val redirectUri = parameters["redirect_uri"]!!
        val client = HttpClient(CIO)
        try {
            client.get("$redirectUri/?code=the-code&state=${state ?: parameters["state"]}")
        } finally {
            client.close()
        }
    }

    private fun cli(
        sink: StringBuilder,
        openBrowser: suspend (String) -> Unit = {},
        httpClientFactory: () -> HttpClient = { HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }) },
    ): TriageCli = TriageCli(StructuredLogger(sink), httpClientFactory, openBrowser = openBrowser)

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
