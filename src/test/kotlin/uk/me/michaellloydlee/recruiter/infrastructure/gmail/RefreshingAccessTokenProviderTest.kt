package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RefreshingAccessTokenProviderTest {
    @Test
    fun returnsTheStoredTokenWhenItIsStillValid() =
        runTest {
            val store = FakeTokenStore(token(NOW.plusSeconds(600)))
            val refresher = FakeRefresher()

            val token = provider(store, refresher).accessToken()

            assertEquals("access-token", token)
            assertEquals(0, refresher.refreshCount)
        }

    @Test
    fun refreshesTheTokenWhenItIsExpired() =
        runTest {
            val store = FakeTokenStore(token(NOW.minusSeconds(10)))
            val refresher = FakeRefresher()

            val token = provider(store, refresher).accessToken()

            assertEquals("refreshed-access-token", token)
            assertEquals(1, refresher.refreshCount)
            assertEquals("refreshed-access-token", store.saved?.accessToken)
        }

    @Test
    fun refreshesTheTokenBeforeItExpires() =
        runTest {
            val store = FakeTokenStore(token(NOW.plusSeconds(30)))
            val refresher = FakeRefresher()

            provider(store, refresher).accessToken()

            assertEquals(1, refresher.refreshCount)
        }

    @Test
    fun failsWhenNoTokenIsStored() =
        runTest {
            val provider = provider(FakeTokenStore(null), FakeRefresher())

            val error = assertFailsWith<GmailAuthenticationException> { provider.accessToken() }

            assertTrue(error.message!!.contains("No Gmail token found"))
        }

    @Test
    fun failsWhenTheStoredTokenHasNoRefreshToken() =
        runTest {
            val store = FakeTokenStore(token(NOW.minusSeconds(10)).copy(refreshToken = null))

            val error = assertFailsWith<GmailAuthenticationException> { provider(store, FakeRefresher()).accessToken() }

            assertTrue(error.message!!.contains("no refresh token"))
        }

    @Test
    fun forcesARefresh() =
        runTest {
            val store = FakeTokenStore(token(NOW.plusSeconds(600)))
            val refresher = FakeRefresher()

            val token = provider(store, refresher).refresh()

            assertEquals("refreshed-access-token", token)
            assertEquals(1, refresher.refreshCount)
        }

    @Test
    fun keepsTheTokenInMemoryAfterTheFirstRefresh() =
        runTest {
            val store = FakeTokenStore(token(NOW.minusSeconds(10)))
            val refresher = FakeRefresher()
            val provider = provider(store, refresher)

            provider.accessToken()
            provider.accessToken()

            assertEquals(1, refresher.refreshCount)
        }

    private fun provider(
        store: OAuth2TokenStore,
        refresher: OAuth2TokenRefresher,
    ): RefreshingAccessTokenProvider =
        RefreshingAccessTokenProvider(
            refresher = refresher,
            tokenStore = store,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            refreshLeeway = Duration.ofSeconds(60),
        )

    private fun token(expiry: Instant): OAuth2Token =
        OAuth2Token(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = expiry,
        )

    private class FakeTokenStore(
        private var token: OAuth2Token?,
    ) : OAuth2TokenStore {
        var saved: OAuth2Token? = null
            private set

        override suspend fun load(): OAuth2Token? = token

        override suspend fun save(token: OAuth2Token) {
            this.token = token
            saved = token
        }
    }

    private class FakeRefresher : OAuth2TokenRefresher {
        var refreshCount: Int = 0
            private set

        override suspend fun refreshAccessToken(refreshToken: String): OAuth2Token {
            refreshCount++
            return OAuth2Token(
                accessToken = "refreshed-access-token",
                refreshToken = refreshToken,
                expiresAt = NOW.plusSeconds(600),
            )
        }
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")
    }
}
