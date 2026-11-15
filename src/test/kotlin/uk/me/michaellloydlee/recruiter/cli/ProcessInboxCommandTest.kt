package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.test
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.run.FileLastRunStore
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessInboxCommandTest {
    @AfterEach
    fun cleanup() {
        cleanupTempDirs()
    }

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
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()} " +
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
                        "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                        "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                            "--token $token --requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                            "--token $token --requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                            "--token $token --requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(0, result.statusCode)
            assertEquals(NOW, FileLastRunStore.nextTo(token).load())
        }

    @Test
    fun warnsWhenTheMomentOfTheLastRunIsInTheFuture() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()
            val token = tokenFile()
            FileLastRunStore.nextTo(token).save(NOW.plusSeconds(60))

            val result =
                cli(sink, Clock.fixed(NOW, ZoneOffset.UTC)) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token $token --requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(0, result.statusCode)
            assertTrue(records(sink).any { it.event == "process-inbox.window_fallback" })
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
                            "--token $token --requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                        "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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
                        "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
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

    @Test
    fun processesTheMessageWithTheGivenIdOnly() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --id message-id " +
                            "--credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(0, result.statusCode)
            val list = engine.requestHistory.filter { it.url.encodedPath.endsWith("/users/me/messages") }
            assertTrue(list.isEmpty())
            val completed = records(sink).last()
            assertEquals("1", completed.attributes["screened"])
            assertEquals("message-id", completed.attributes["id"])
        }

    @Test
    fun aRunOfAMessageDoesNotRememberTheMomentOfTheRun() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()
            val token = tokenFile()

            val result =
                cli(sink, Clock.fixed(NOW, ZoneOffset.UTC)) { mockClient(engine) }
                    .test(
                        "process-inbox --id message-id " +
                            "--credentials ${credentialsFile()} " +
                            "--token $token --requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(0, result.statusCode)
            assertNull(FileLastRunStore.nextTo(token).load())
        }

    @Test
    fun aRunOfAMessageHasNoWindowInTheLog() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink, Clock.fixed(NOW, ZoneOffset.UTC)) { mockClient(engine) }
                    .test(
                        "process-inbox --id message-id " +
                            "--credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} --requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(0, result.statusCode)
            val completed = records(sink).last()
            assertNull(completed.attributes["window"])
            assertNull(completed.attributes["since"])
        }

    @Test
    fun failsOnAnIdThatIsEmpty() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink)
                    .test(
                        "process-inbox --id= " +
                            "--credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(1, result.statusCode)
            assertTrue(result.output.contains("The id of a message is not empty"))
        }

    @Test
    fun usesTheRulesOfTheFile() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()
            val rules = rulesFile("""{"job_listing_phrases": ["your cv"]}""")

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()} " +
                            "--rules $rules",
                    )

            assertEquals(0, result.statusCode)
            val completed = records(sink).last()
            assertEquals("1", completed.attributes["job_listings"])
            assertEquals("0", completed.attributes["recruiters"])
        }

    @Test
    fun aRuleTheFileReplacesIsNotUsed() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()
            val rules =
                rulesFile("""{"recruiter_keywords": ["zzz-not-in-the-message"], "recruiter_sender_hints": []}""")

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()} " +
                            "--rules $rules",
                    )

            assertEquals(0, result.statusCode)
            val completed = records(sink).last()
            assertEquals("0", completed.attributes["recruiters"])
            assertEquals("1", completed.attributes["filtered_out"])
            assertTrue(engine.requestHistory.none { it.url.encodedPath.endsWith("/v1/systemone") })
        }

    @Test
    fun failsWhenTheRulesFileIsNotReadable() =
        runTest {
            val sink = StringBuilder()
            val rules = tempDir("rules")

            val result =
                cli(sink).test(
                    "process-inbox --credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()} " +
                        "--rules $rules",
                )

            assertEquals(1, result.statusCode)
            assertTrue(result.output.contains("could not be read"))
        }

    @Test
    fun failsWhenTheRulesFileIsNotValidJson() =
        runTest {
            val sink = StringBuilder()
            val rules = rulesFile("not json at all")

            val result =
                cli(sink).test(
                    "process-inbox --credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()} " +
                        "--rules $rules",
                )

            assertEquals(1, result.statusCode)
            assertTrue(result.output.contains("could not be read"))
        }

    @Test
    fun usesTheRulesOfTheCodeWhenTheDefaultRulesFileIsMissing() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()
            val missing = tempDir("rules").resolve("rules.json")
            val command =
                ProcessInboxCommand(
                    StructuredLogger(sink),
                    { mockClient(engine) },
                    Clock.systemUTC(),
                    missing,
                )

            val result =
                command.test(
                    "--credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()}",
                )

            assertEquals(0, result.statusCode)
            assertTrue(records(sink).any { it.event == "process-inbox.rules_default" })
        }

    @Test
    fun failsWhenAnExplicitlyNamedRulesFileDoesNotExist() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink).test(
                    "process-inbox --credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()} " +
                        "--rules ${missingRulesFile()}",
                )

            assertEquals(1, result.statusCode)
            assertTrue(result.output.contains("Rules file not found"))
        }
}
