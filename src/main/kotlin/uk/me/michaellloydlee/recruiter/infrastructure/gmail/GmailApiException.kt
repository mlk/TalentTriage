package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Raised when the Gmail REST API answers with an unexpected status code.
 *
 * @property statusCode the HTTP status code of the response.
 * @property body the body of the response, kept for diagnostics.
 */
class GmailApiException(
    val statusCode: Int,
    val body: String,
) : RuntimeException("Gmail API request failed with status $statusCode: $body")
