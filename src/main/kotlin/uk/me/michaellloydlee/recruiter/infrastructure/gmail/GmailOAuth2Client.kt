package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant

/**
 * The OAuth2 flows of the Gmail API.
 *
 * The client knows how to build the authorisation URL the user has to open,
 * how to exchange the authorisation code for a token and how to refresh an
 * access token with the refresh token.
 *
 * @param httpClient the client used for the token requests.
 * @param credentials the client credentials of the local Google application.
 * @param clock source of the current time, used for the expiry of the token.
 * @param json parser used for the token responses.
 */
class GmailOAuth2Client(
    private val httpClient: HttpClient,
    private val credentials: GoogleClientCredentials,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OAuth2TokenRefresher {
    /**
     * Builds the URL the user has to open in order to authorise the application.
     *
     * @param redirectUri the redirect URI registered for the application.
     * @param scopes the scopes to request.
     * @param state an opaque value that is echoed back by the authorisation server.
     */
    fun authorizationUrl(
        redirectUri: String,
        scopes: List<String>,
        state: String,
    ): String =
        URLBuilder(credentials.authUri)
            .apply {
                parameters.append(PARAM_CLIENT_ID, credentials.clientId)
                parameters.append(PARAM_REDIRECT_URI, redirectUri)
                parameters.append(PARAM_RESPONSE_TYPE, RESPONSE_TYPE_CODE)
                parameters.append(PARAM_SCOPE, scopes.joinToString(separator = SCOPE_SEPARATOR))
                parameters.append(PARAM_ACCESS_TYPE, ACCESS_TYPE_OFFLINE)
                parameters.append(PARAM_PROMPT, PROMPT_CONSENT)
                parameters.append(PARAM_STATE, state)
            }.buildString()

    /**
     * Exchanges an authorisation code for an access token.
     *
     * @param code the authorisation code the user received from Google.
     * @param redirectUri the redirect URI the authorisation was started with.
     */
    suspend fun exchangeAuthorizationCode(
        code: String,
        redirectUri: String,
    ): OAuth2Token =
        requestToken(
            parameters =
                Parameters.build {
                    append(PARAM_GRANT_TYPE, GRANT_TYPE_AUTHORIZATION_CODE)
                    append(PARAM_CODE, code)
                    append(PARAM_CLIENT_ID, credentials.clientId)
                    append(PARAM_CLIENT_SECRET, credentials.clientSecret)
                    append(PARAM_REDIRECT_URI, redirectUri)
                },
        )

    /**
     * Refreshes an access token with the refresh token.
     *
     * Google does not send a new refresh token when the old one is still
     * valid, the old one is kept in that case.
     *
     * @param refreshToken the refresh token of the application.
     */
    override suspend fun refreshAccessToken(refreshToken: String): OAuth2Token =
        requestToken(
            parameters =
                Parameters.build {
                    append(PARAM_GRANT_TYPE, GRANT_TYPE_REFRESH_TOKEN)
                    append(PARAM_REFRESH_TOKEN, refreshToken)
                    append(PARAM_CLIENT_ID, credentials.clientId)
                    append(PARAM_CLIENT_SECRET, credentials.clientSecret)
                },
            fallbackRefreshToken = refreshToken,
        )

    private suspend fun requestToken(
        parameters: Parameters,
        fallbackRefreshToken: String? = null,
    ): OAuth2Token {
        val response =
            httpClient.post(credentials.tokenUri) {
                expectSuccess = false
                setBody(FormDataContent(parameters))
            }
        val body = response.bodyAsText()
        val payload =
            try {
                json.decodeFromString(TokenResponse.serializer(), body)
            } catch (error: SerializationException) {
                throw OAuth2Exception(
                    error = ERROR_INVALID_RESPONSE,
                    errorDescription = error.message,
                    statusCode = response.status.value,
                )
            }
        if (!response.status.isSuccess() || payload.error != null) {
            throw OAuth2Exception(
                error = payload.error ?: ERROR_UNKNOWN,
                errorDescription = payload.errorDescription,
                statusCode = response.status.value,
            )
        }
        return payload.toToken(clock.instant(), fallbackRefreshToken)
    }

    private fun TokenResponse.toToken(
        now: Instant,
        fallbackRefreshToken: String?,
    ): OAuth2Token {
        val accessToken =
            accessToken
                ?: throw OAuth2Exception(
                    error = ERROR_INVALID_RESPONSE,
                    errorDescription = "The token response did not contain an access token",
                    statusCode = STATUS_OK,
                )
        return OAuth2Token(
            accessToken = accessToken,
            refreshToken = refreshToken ?: fallbackRefreshToken,
            expiresAt = expiresIn?.let { now.plusSeconds(it) },
            tokenType = tokenType,
            scope = scope,
        )
    }

    companion object {
        private const val PARAM_CLIENT_ID = "client_id"
        private const val PARAM_CLIENT_SECRET = "client_secret"
        private const val PARAM_CODE = "code"
        private const val PARAM_GRANT_TYPE = "grant_type"
        private const val PARAM_REDIRECT_URI = "redirect_uri"
        private const val PARAM_REFRESH_TOKEN = "refresh_token"
        private const val PARAM_ACCESS_TYPE = "access_type"
        private const val PARAM_PROMPT = "prompt"
        private const val PARAM_RESPONSE_TYPE = "response_type"
        private const val PARAM_SCOPE = "scope"
        private const val PARAM_STATE = "state"

        private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        private const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
        private const val RESPONSE_TYPE_CODE = "code"
        private const val ACCESS_TYPE_OFFLINE = "offline"
        private const val PROMPT_CONSENT = "consent"
        private const val SCOPE_SEPARATOR = " "

        private const val ERROR_INVALID_RESPONSE = "invalid_response"
        private const val ERROR_UNKNOWN = "unknown_error"
        private const val STATUS_OK = 200
    }
}
