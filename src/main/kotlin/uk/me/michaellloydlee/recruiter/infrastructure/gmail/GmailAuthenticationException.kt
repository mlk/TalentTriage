package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Raised when the application has no usable Gmail credentials or tokens.
 *
 * @param message what is missing and how to obtain it.
 * @param cause the exception that caused the failure.
 */
class GmailAuthenticationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
