package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.me.michaellloydlee.recruiter.infrastructure.serialization.InstantSerializer
import java.time.Duration
import java.time.Instant

/**
 * The OAuth2 token of the local Google application.
 *
 * @property accessToken the bearer token sent to the Gmail API.
 * @property refreshToken the token used to obtain a new access token, `null` when the token was not issued offline.
 * @property expiresAt the moment the access token expires, `null` when it is unknown.
 * @property tokenType the type of the access token, `Bearer` for Google.
 * @property scope the scopes the token was granted for.
 */
@Serializable
data class OAuth2Token(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_at")
    @Serializable(with = InstantSerializer::class)
    val expiresAt: Instant? = null,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    val scope: String? = null,
) {
    /**
     * Tells whether the access token can still be used at the given moment.
     *
     * A token without a known expiry is treated as valid for [defaultTtl],
     * the lifetime Google grants an access token. Refreshing it on every
     * single call would be pointless, and a token the API rejects is
     * refreshed anyway.
     *
     * @param instant the moment to check the token at.
     * @param leeway safety margin, the token is treated as expired that much earlier.
     * @param defaultTtl lifetime assumed for a token without an expiry.
     */
    fun isValidAt(
        instant: Instant,
        leeway: Duration = Duration.ZERO,
        defaultTtl: Duration = DEFAULT_TTL,
    ): Boolean {
        val expiry = expiresAt ?: return instant.isBefore(instant.plus(defaultTtl))
        return instant.isBefore(expiry.minus(leeway))
    }

    companion object {
        /** The lifetime Google grants an access token without an explicit expiry. */
        val DEFAULT_TTL: Duration = Duration.ofHours(1)
    }
}
