package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The answer of the Google OAuth2 token endpoint.
 */
@Serializable
internal data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Long? = null,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description")
    val errorDescription: String? = null,
)
