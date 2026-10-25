package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.coroutines.delay

/**
 * A token store that keeps the token in memory.
 *
 * @param token the token the store starts with.
 */
class FakeTokenStore(
    private var token: OAuth2Token?,
) : OAuth2TokenStore {
    /** The token of the last [save]. */
    var saved: OAuth2Token? = null
        private set

    override suspend fun load(): OAuth2Token? = token

    override suspend fun save(token: OAuth2Token) {
        this.token = token
        saved = token
    }
}

/**
 * A refresher that answers with a fixed token.
 *
 * @param refreshedToken the token the refresher returns.
 */
class FakeRefresher(
    private val refreshedToken: OAuth2Token,
) : OAuth2TokenRefresher {
    /** How often the refresher was asked for a token. */
    var refreshCount: Int = 0
        private set

    override suspend fun refreshAccessToken(refreshToken: String): OAuth2Token {
        refreshCount++
        // The delay lets concurrent callers overlap, like the token endpoint does.
        delay(1)
        return refreshedToken
    }
}
