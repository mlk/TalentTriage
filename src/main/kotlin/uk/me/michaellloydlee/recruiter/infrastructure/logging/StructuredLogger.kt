package uk.me.michaellloydlee.recruiter.infrastructure.logging

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant

/**
 * Writes structured log records - one JSON object per line - to a sink.
 *
 * The sink is an [Appendable] so that the log output can be redirected in
 * tests; the executable writes to standard output.
 *
 * @param sink destination of the serialised records.
 * @param clock source of the record timestamps.
 * @param json encoder used to serialise the records.
 */
class StructuredLogger(
    private val sink: Appendable,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json,
) {
    /**
     * Records an informational event.
     *
     * @param event stable, machine readable name of the event.
     * @param message human readable description of the event.
     * @param attributes additional key/value pairs of the event.
     */
    fun info(
        event: String,
        message: String,
        attributes: Map<String, String> = emptyMap(),
    ) = log(LogLevel.INFO, event, message, attributes)

    /**
     * Records a warning.
     *
     * @param event stable, machine readable name of the event.
     * @param message human readable description of the event.
     * @param attributes additional key/value pairs of the event.
     */
    fun warn(
        event: String,
        message: String,
        attributes: Map<String, String> = emptyMap(),
    ) = log(LogLevel.WARN, event, message, attributes)

    /**
     * Records an error.
     *
     * @param event stable, machine readable name of the event.
     * @param message human readable description of the event.
     * @param attributes additional key/value pairs of the event.
     */
    fun error(
        event: String,
        message: String,
        attributes: Map<String, String> = emptyMap(),
    ) = log(LogLevel.ERROR, event, message, attributes)

    private fun log(
        level: LogLevel,
        event: String,
        message: String,
        attributes: Map<String, String>,
    ) {
        val record =
            LogRecord(
                timestamp = Instant.now(clock).toString(),
                level = level,
                event = event,
                message = message,
                attributes = attributes,
            )
        sink.append(json.encodeToString(record)).append('\n')
    }
}
