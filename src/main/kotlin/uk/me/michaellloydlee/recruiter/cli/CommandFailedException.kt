package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.core.CliktError

/**
 * Reports a failed command to the user.
 *
 * The message is printed to standard error and the process exits with the
 * status code 1.
 *
 * @param message what went wrong.
 * @param cause the exception that caused the failure.
 */
class CommandFailedException(
    message: String,
    cause: Exception? = null,
) : CliktError(
        message = message,
        cause = cause,
        statusCode = 1,
        printError = true,
    )
