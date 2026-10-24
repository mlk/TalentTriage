package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuth2TokenTest {
    @Test
    fun isValidBeforeItExpires() {
        val token = token(NOW.plusSeconds(60))

        assertTrue(token.isValidAt(NOW))
    }

    @Test
    fun isNotValidAfterItExpires() {
        val token = token(NOW.minusSeconds(1))

        assertFalse(token.isValidAt(NOW))
    }

    @Test
    fun isNotValidWithoutAnExpiry() {
        val token = OAuth2Token(accessToken = "access-token")

        assertFalse(token.isValidAt(NOW))
    }

    @Test
    fun expiresEarlyWithTheLeeway() {
        val token = token(NOW.plusSeconds(30))

        assertTrue(token.isValidAt(NOW))
        assertFalse(token.isValidAt(NOW, leeway = Duration.ofSeconds(60)))
    }

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
