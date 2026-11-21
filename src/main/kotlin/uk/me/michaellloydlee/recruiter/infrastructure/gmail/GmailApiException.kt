package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Raised when the Gmail REST API answers with an unexpected answer.
 *
 * @property statusCode the HTTP status code of the response.
 * @property body the body of the response, kept for diagnostics.
 * @param cause the exception that caused the failure, when the answer could
 *   not be read.
 */
class GmailApiException(
    val statusCode: Int,
    val body: String,
    cause: Throwable? = null,
) : RuntimeException("Gmail API request failed with status $statusCode: $body", cause)
