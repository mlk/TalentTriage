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
            val store = temporaryTokenStore(token(NOW.plusSeconds(600)))
            val refresher = refresher()

            val token = provider(store, refresher.client).accessToken()

            assertEquals("access-token", token)
            assertEquals(0, refresher.refreshCount)
        }

    @Test
    fun refreshesTheTokenWhenItIsExpired() =
        runTest {
            val store = temporaryTokenStore(token(NOW.minusSeconds(10)))
            val refresher = refresher()

            val token = provider(store, refresher.client).accessToken()

            assertEquals("refreshed-access-token", token)
            assertEquals(1, refresher.refreshCount)
            assertEquals("refreshed-access-token", store.load()?.accessToken)
        }

    @Test
    fun refreshesTheTokenBeforeItExpires() =
        runTest {
            val store = temporaryTokenStore(token(NOW.plusSeconds(30)))
            val refresher = refresher()

            provider(store, refresher.client).accessToken()

            assertEquals(1, refresher.refreshCount)
        }

    @Test
    fun failsWhenNoTokenIsStored() =
        runTest {
            val provider = provider(temporaryTokenStore(null), refresher().client)

            val error = assertFailsWith<GmailAuthenticationException> { provider.accessToken() }

            assertTrue(error.message!!.contains("No Gmail token found"))
        }

    @Test
    fun failsWhenTheStoredTokenHasNoRefreshToken() =
        runTest {
            val store = temporaryTokenStore(token(NOW.minusSeconds(10)).copy(refreshToken = null))

            val error =
                assertFailsWith<GmailAuthenticationException> {
                    provider(store, refresher().client).accessToken()
                }

            assertTrue(error.message!!.contains("no refresh token"))
        }

    @Test
    fun forcesARefresh() =
        runTest {
            val store = temporaryTokenStore(token(NOW.plusSeconds(600)))
            val refresher = refresher()

            val token = provider(store, refresher.client).refresh()

            assertEquals("refreshed-access-token", token)
            assertEquals(1, refresher.refreshCount)
        }

    @Test
    fun keepsTheTokenInMemoryAfterTheFirstRefresh() =
        runTest {
            val store = temporaryTokenStore(token(NOW.minusSeconds(10)))
            val refresher = refresher()
            val provider = provider(store, refresher.client)

            provider.accessToken()
            provider.accessToken()

            assertEquals(1, refresher.refreshCount)
        }

    @Test
    fun refreshesOnlyOnceForConcurrentCallers() =
        runTest {
            val store = temporaryTokenStore(token(NOW.minusSeconds(10)))
            val refresher = refresher(answerDelay = Duration.ofMillis(1))
            val provider = provider(store, refresher.client)

            val tokens = listOf(async { provider.accessToken() }, async { provider.accessToken() })
            tokens.awaitAll()

            assertEquals(1, refresher.refreshCount)
            assertEquals(listOf("refreshed-access-token", "refreshed-access-token"), tokens.map { it.await() })
        }

    private fun provider(
        store: FileOAuth2TokenStore,
        refresher: GmailOAuth2Client,
    ): RefreshingAccessTokenProvider =
        RefreshingAccessTokenProvider(
            refresher = refresher,
            tokenStore = store,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            refreshLeeway = Duration.ofSeconds(60),
        )

    private fun refresher(answerDelay: Duration = Duration.ZERO): TestOAuth2Client =
        TestOAuth2Client(
            accessToken = "refreshed-access-token",
            answerDelay = answerDelay,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )

    private fun token(expiry: Instant): OAuth2Token =
        OAuth2Token(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = expiry,
        )

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-24T08:00:00Z")
    }
}
