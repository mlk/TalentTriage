package uk.me.michaellloydlee.recruiter.infrastructure.gmail

/**
 * Stores the OAuth2 token of the application between the runs.
 */
interface OAuth2TokenStore {
    /**
     * Reads the stored token.
     *
     * @return the stored token, or `null` when nothing was stored yet.
     */
    suspend fun load(): OAuth2Token?

    /**
     * Writes the token, replacing the stored one.
     *
     * @param token the token to store.
     */
    suspend fun save(token: OAuth2Token)
}
