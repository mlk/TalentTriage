package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Renews the access token of the application.
 */
interface OAuth2TokenRefresher {
    /**
     * Exchanges a refresh token for a new access token.
     *
     * @param refreshToken the refresh token of the application.
     */
    suspend fun refreshAccessToken(refreshToken: String): OAuth2Token
}
