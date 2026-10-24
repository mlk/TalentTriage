package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogLevel
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessInboxCommandTest {
    @Test
    fun processInboxExitsSuccessfully() {
        val result = cli().test("process-inbox")

        assertEquals(0, result.statusCode)
    }

    @Test
    fun processInboxPrintsStructuredOutput() {
        val sink = StringBuilder()

        cli(sink).test("process-inbox")

        val records =
            sink
                .toString()
                .trim()
                .lines()
                .map { Json.decodeFromString<LogRecord>(it) }
        assertEquals(
            listOf("process-inbox.started", "process-inbox.completed"),
            records.map { it.event },
        )
        assertTrue(records.all { it.level == LogLevel.INFO })
    }

    @Test
    fun processInboxRecordsTheTrigger() {
        val sink = StringBuilder()

        cli(sink).test("process-inbox")

        val first =
            Json.decodeFromString<LogRecord>(
                sink
                    .toString()
                    .trim()
                    .lines()
                    .first(),
            )
        assertEquals(
            mapOf(ProcessInboxCommand.TRIGGER_ATTRIBUTE to ProcessInboxCommand.TRIGGER_CLI),
            first.attributes,
        )
    }

    @Test
    fun unknownCommandFails() {
        val result = cli().test("does-not-exist")

        assertEquals(1, result.statusCode)
    }

    @Test
    fun helpIsPrintedWithoutASubcommand() {
        val result = cli().test("")

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains(ProcessInboxCommand.COMMAND_NAME))
    }

    private fun cli(sink: StringBuilder = StringBuilder()): TriageCli = TriageCli(StructuredLogger(sink))
}
