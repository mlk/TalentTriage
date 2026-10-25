package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            val refresher = FakeRefresher(refreshedToken())

            val token = provider(store, refresher).accessToken()

            assertEquals("access-token", token)
            assertEquals(0, refresher.refreshCount)
        }

    @Test
    fun refreshesTheTokenWhenItIsExpired() =
        runTest {
            val store = FakeTokenStore(token(NOW.minusSeconds(10)))
            val refresher = FakeRefresher(refreshedToken())

            val token = provider(store, refresher).accessToken()

            assertEquals("refreshed-access-token", token)
            assertEquals(1, refresher.refreshCount)
            assertEquals("refreshed-access-token", store.saved?.accessToken)
        }

    @Test
    fun refreshesTheTokenBeforeItExpires() =
        runTest {
            val store = FakeTokenStore(token(NOW.plusSeconds(30)))
            val refresher = FakeRefresher(refreshedToken())

            provider(store, refresher).accessToken()

            assertEquals(1, refresher.refreshCount)
        }

    @Test
    fun failsWhenNoTokenIsStored() =
        runTest {
            val provider = provider(FakeTokenStore(null), FakeRefresher(refreshedToken()))

            val error = assertFailsWith<GmailAuthenticationException> { provider.accessToken() }

            assertTrue(error.message!!.contains("No Gmail token found"))
        }

    @Test
    fun failsWhenTheStoredTokenHasNoRefreshToken() =
        runTest {
            val store = FakeTokenStore(token(NOW.minusSeconds(10)).copy(refreshToken = null))

            val error =
                assertFailsWith<GmailAuthenticationException> {
                    provider(store, FakeRefresher(refreshedToken())).accessToken()
                }

            assertTrue(error.message!!.contains("no refresh token"))
        }

    @Test
    fun forcesARefresh() =
        runTest {
            val store = FakeTokenStore(token(NOW.plusSeconds(600)))
            val refresher = FakeRefresher(refreshedToken())

            val token = provider(store, refresher).refresh()

            assertEquals("refreshed-access-token", token)
            assertEquals(1, refresher.refreshCount)
        }

    @Test
    fun keepsTheTokenInMemoryAfterTheFirstRefresh() =
        runTest {
            val store = FakeTokenStore(token(NOW.minusSeconds(10)))
            val refresher = FakeRefresher(refreshedToken())
            val provider = provider(store, refresher)

            provider.accessToken()
            provider.accessToken()

            assertEquals(1, refresher.refreshCount)
        }

    @Test
    fun refreshesOnlyOnceForConcurrentCallers() =
        runTest {
            val store = FakeTokenStore(token(NOW.minusSeconds(10)))
            val refresher = FakeRefresher(refreshedToken())
            val provider = provider(store, refresher)

            val tokens = listOf(async { provider.accessToken() }, async { provider.accessToken() })
            tokens.awaitAll()

            assertEquals(1, refresher.refreshCount)
            assertEquals(listOf("refreshed-access-token", "refreshed-access-token"), tokens.map { it.await() })
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

    private fun refreshedToken(): OAuth2Token =
        OAuth2Token(
            accessToken = "refreshed-access-token",
            refreshToken = "refresh-token",
            expiresAt = NOW.plusSeconds(600),
        )

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")
    }
}
