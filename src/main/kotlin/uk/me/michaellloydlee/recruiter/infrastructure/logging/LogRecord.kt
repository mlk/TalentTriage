package uk.me.michaellloydlee.recruiter.infrastructure.logging

import kotlinx.serialization.Serializable

/**
 * A single log record, serialised as one JSON object per line.
 *
 * @property timestamp ISO-8601 instant the record was created at.
 * @property level severity of the record.
 * @property event stable, machine readable name of the event.
 * @property message human readable description of the event.
 * @property attributes additional key/value pairs of the event.
 */
@Serializable
data class LogRecord(
    val timestamp: String,
    val level: LogLevel,
    val event: String,
    val message: String,
    val attributes: Map<String, String> = emptyMap(),
)
