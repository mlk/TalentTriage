package uk.me.michaellloydlee.recruiter.cli

import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger

/**
 * The event of the log record of a failed run.
 */
const val FAILURE_EVENT: String = "recruiter-cli.failed"

/**
 * Records a failure of the run.
 *
 * The message holds the stack trace of the exception: the log of a run is one
 * JSON object per line, the stack trace of an exception nobody expected
 * belongs into the record and not onto standard error.
 *
 * @param error the exception that ended the run.
 */
internal fun StructuredLogger.logFailure(error: Throwable) {
    error(
        event = FAILURE_EVENT,
        message = failureMessage(error),
    )
}

/**
 * The message of a failed run.
 *
 * @param error the exception that ended the run.
 * @return the stack trace of the exception, it holds the message of the
 *   exception as well.
 */
internal fun failureMessage(error: Throwable): String = error.stackTraceToString()
