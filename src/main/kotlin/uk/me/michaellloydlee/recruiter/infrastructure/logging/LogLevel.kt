package uk.me.michaellloydlee.recruiter.infrastructure.logging

import kotlinx.serialization.Serializable

/**
 * Severity of a structured log record.
 */
@Serializable
enum class LogLevel {
    /** Informational progress of a run. */
    INFO,

    /** Something unexpected happened, but the run continues. */
    WARN,

    /** The run failed. */
    ERROR,
}
