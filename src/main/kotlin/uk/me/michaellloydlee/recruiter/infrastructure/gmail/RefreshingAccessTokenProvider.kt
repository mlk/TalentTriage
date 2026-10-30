package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.coroutines.sync.Mutex
import java.time.Clock
import java.time.Duration

/**
 * Provides the access token of the Gmail API and refreshes it when needed.
 *
 * The token is read from the [tokenStore] and kept in memory afterwards, so a
 * run that makes several API calls refreshes at most once. The refreshed token
 * is written back to the store, because the refresh token outlives the access
 * token.
 *
 * The check whether the token is still valid and the refresh itself happen
 * under the same lock, so concurrent callers cannot trigger two refreshes for
 * the same expired token.
 *
 * @param refresher used to renew the access token.
 * @param tokenStore store the token is read from and written to.
 * @param clock source of the current time.
 * @param refreshLeeway safety margin, a token is refreshed that much before it expires.
 */
class RefreshingAccessTokenProvider(
    private val refresher: GmailOAuth2Client,
    private val tokenStore: OAuth2TokenStore,
    private val clock: Clock = Clock.systemUTC(),
    private val refreshLeeway: Duration = DEFAULT_REFRESH_LEEWAY,
) {
    private val mutex = Mutex()
    private var cached: OAuth2Token? = null

    /**
     * Returns an access token that can be used right now.
     *
     * The token is refreshed when it is missing or expired.
     */
    suspend fun accessToken(): String {
        mutex.lock()
        try {
            val known = cached ?: tokenStore.load()
            if (known != null && known.isValidAt(clock.instant(), refreshLeeway)) {
                cached = known
                return known.accessToken
            }
            return refreshLocked(known).accessToken
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Forces a refresh and returns the new access token.
     *
     * Used when the Gmail API rejects a token that was expected to be valid.
     */
    suspend fun refresh(): String {
        mutex.lock()
        try {
            return refreshLocked(cached ?: tokenStore.load()).accessToken
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun refreshLocked(stored: OAuth2Token?): OAuth2Token {
        val token =
            stored
                ?: throw GmailAuthenticationException(
                    "No Gmail token found - run `recruiter-cli authenticate` first",
                )
        val refreshToken =
            token.refreshToken
                ?: throw GmailAuthenticationException(
                    "The stored Gmail token has no refresh token - run `recruiter-cli authenticate` again",
                )
        val refreshed = refresher.refreshAccessToken(refreshToken)
        tokenStore.save(refreshed)
        cached = refreshed
        return refreshed
    }

    companion object {
        /** A token is refreshed a minute before it actually expires. */
        val DEFAULT_REFRESH_LEEWAY: Duration = Duration.ofSeconds(60)
    }
}
