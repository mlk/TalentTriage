package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogLevel
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecruiterCliTest {
    @Test
    fun logsAnUnexpectedFailureAsOneJsonRecord() =
        runTest {
            val sink = StringBuilder()

            val code = runCli(arrayOf(), StructuredLogger(sink), failingCommand())

            assertEquals(1, code)
            val record = record(sink)
            assertEquals(LogLevel.ERROR, record.level)
            assertEquals(FAILURE_EVENT, record.event)
            assertTrue(record.message.contains("the model exploded"))
            assertTrue(record.message.contains("IllegalStateException"))
        }

    @Test
    fun theRecordOfTheFailureIsOneLine() =
        runTest {
            val sink = StringBuilder()

            runCli(arrayOf(), StructuredLogger(sink), failingCommand())

            assertEquals(
                1,
                sink
                    .toString()
                    .trim()
                    .lines()
                    .size,
            )
        }

    @Test
    fun aRunThatSucceedsLogsNothing() =
        runTest {
            val sink = StringBuilder()

            val code = runCli(arrayOf(), StructuredLogger(sink), succeedingCommand())

            assertEquals(0, code)
            assertEquals("", sink.toString())
        }

    private fun failingCommand(): SuspendingCliktCommand =
        object : SuspendingCliktCommand(name = "failing") {
            override suspend fun run() {
                error("the model exploded")
            }
        }

    private fun succeedingCommand(): SuspendingCliktCommand =
        object : SuspendingCliktCommand(name = "succeeding") {
            override suspend fun run() = Unit
        }

    private fun record(sink: StringBuilder): LogRecord = Json.decodeFromString(sink.toString().trim())
}
