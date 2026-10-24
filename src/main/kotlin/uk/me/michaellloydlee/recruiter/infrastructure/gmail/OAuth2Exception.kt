package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Raised when the OAuth2 token endpoint rejects a request.
 *
 * @property error the error code of the response, for example `invalid_grant`.
 * @property errorDescription the description of the error, when the endpoint sent one.
 * @property statusCode the HTTP status code of the response.
 */
class OAuth2Exception(
    val error: String,
    val errorDescription: String?,
    val statusCode: Int,
) : RuntimeException(
        "OAuth2 request failed with $error" +
            (errorDescription?.let { ": $it" } ?: "") +
            " (status $statusCode)",
    )
