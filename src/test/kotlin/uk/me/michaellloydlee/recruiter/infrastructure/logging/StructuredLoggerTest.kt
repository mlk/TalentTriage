package uk.me.michaellloydlee.recruiter.infrastructure.logging

import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuredLoggerTest {
    @Test
    fun writesOneJsonObjectPerRecord() {
        val sink = StringBuilder()

        StructuredLogger(sink, FIXED_CLOCK).info("run.started", "Run started")

        assertEquals(
            1,
            sink
                .toString()
                .trim()
                .lines()
                .size,
        )
        assertTrue(sink.toString().startsWith("{"))
        assertTrue(sink.toString().endsWith("}\n"))
    }

    @Test
    fun serialisesTheRecordFields() {
        val sink = StringBuilder()

        StructuredLogger(sink, FIXED_CLOCK).info(
            event = "run.started",
            message = "Run started",
            attributes = mapOf("trigger" to "cli"),
        )

        val record = Json.decodeFromString<LogRecord>(sink.toString())
        assertEquals("2026-10-24T07:00:00Z", record.timestamp)
        assertEquals(LogLevel.INFO, record.level)
        assertEquals("run.started", record.event)
        assertEquals("Run started", record.message)
        assertEquals(mapOf("trigger" to "cli"), record.attributes)
    }

    @Test
    fun writesTheSeverityOfTheRecord() {
        val sink = StringBuilder()
        val logger = StructuredLogger(sink, FIXED_CLOCK)

        logger.info("run.info", "Info")
        logger.warn("run.warn", "Warn")
        logger.error("run.error", "Error")

        val levels =
            sink
                .toString()
                .trim()
                .lines()
                .map { Json.decodeFromString<LogRecord>(it).level }
        assertEquals(listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR), levels)
    }

    @Test
    fun writesTheRecordsInOrder() {
        val sink = StringBuilder()
        val logger = StructuredLogger(sink, FIXED_CLOCK)

        logger.info("run.first", "First")
        logger.info("run.second", "Second")

        val events =
            sink
                .toString()
                .trim()
                .lines()
                .map { Json.decodeFromString<LogRecord>(it).event }
        assertEquals(listOf("run.first", "run.second"), events)
    }

    @Test
    fun aRecordWithoutAttributesHasAnEmptyAttributeMap() {
        val record =
            LogRecord(
                timestamp = "2026-10-24T07:00:00Z",
                level = LogLevel.INFO,
                event = "run.started",
                message = "Run started",
            )

        assertEquals(emptyMap(), record.attributes)
    }

    companion object {
        private val FIXED_CLOCK: Clock =
            Clock.fixed(
                Instant.parse("2026-10-24T07:00:00Z"),
                ZoneOffset.UTC,
            )
    }
}
