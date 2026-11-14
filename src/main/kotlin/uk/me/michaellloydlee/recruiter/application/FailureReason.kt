package uk.me.michaellloydlee.recruiter.application

/**
 * The reason of a failure.
 *
 * The name of the error is part of the reason: an operator that reads the log
 * has to know what went wrong, and an error without a message has a name
 * alone.
 *
 * @param error the error.
 * @return the reason of the failure.
 */
internal fun reasonOf(error: Exception): String =
    error.message?.let { "${error.javaClass.simpleName}: $it" } ?: error.javaClass.simpleName
