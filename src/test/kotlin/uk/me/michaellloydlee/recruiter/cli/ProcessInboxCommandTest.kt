package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogLevel
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessInboxCommandTest {
    @Test
    fun processInboxExitsSuccessfully() =
        runTest {
            val result = cli().test("process-inbox")

            assertEquals(0, result.statusCode)
        }

    @Test
    fun processInboxPrintsStructuredOutput() =
        runTest {
            val sink = StringBuilder()

            cli(sink).test("process-inbox")

            val records = records(sink)
            assertEquals(
                listOf("process-inbox.started", "process-inbox.completed"),
                records.map { it.event },
            )
            assertTrue(records.all { it.level == LogLevel.INFO })
        }

    @Test
    fun processInboxRecordsTheTrigger() =
        runTest {
            val sink = StringBuilder()

            cli(sink).test("process-inbox")

            val first = records(sink).first()
            assertEquals(
                mapOf(ProcessInboxCommand.TRIGGER_ATTRIBUTE to ProcessInboxCommand.TRIGGER_CLI),
                first.attributes,
            )
        }

    @Test
    fun unknownCommandFails() =
        runTest {
            val result = cli().test("does-not-exist")

            assertEquals(1, result.statusCode)
        }

    @Test
    fun helpIsPrintedWithoutASubcommand() =
        runTest {
            val result = cli().test("")

            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains(ProcessInboxCommand.COMMAND_NAME))
        }

    private fun records(sink: StringBuilder): List<LogRecord> =
        sink
            .toString()
            .trim()
            .lines()
            .map { Json.decodeFromString<LogRecord>(it) }

    private fun cli(sink: StringBuilder = StringBuilder()): TriageCli = TriageCli(StructuredLogger(sink))
}
