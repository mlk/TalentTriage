package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Provides the access token that authenticates the Gmail API requests.
 */
interface AccessTokenProvider {
    /**
     * Returns an access token that can be used right now.
     *
     * The token is refreshed when it is missing or expired.
     */
    suspend fun accessToken(): String

    /**
     * Forces a refresh and returns the new access token.
     *
     * Used when the Gmail API rejects a token that was expected to be valid.
     */
    suspend fun refresh(): String
}
