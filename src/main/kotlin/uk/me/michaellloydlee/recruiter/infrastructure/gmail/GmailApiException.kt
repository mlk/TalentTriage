package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Raised when the Gmail REST API answers with an unexpected answer.
 *
 * @property statusCode the HTTP status code of the response.
 * @property body the body of the response, kept for diagnostics, the answer
 *   of an answer that could not be read is truncated.
 * @param cause the exception that caused the failure, when the answer could
 *   not be read.
 * @param message the message of the exception, defaults to the failure of the
 *   request with the status code and the body.
 */
class GmailApiException(
    val statusCode: Int,
    val body: String,
    cause: Throwable? = null,
    message: String = "Gmail API request failed with status $statusCode: $body",
) : RuntimeException(message, cause)
