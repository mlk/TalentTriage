package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * The OAuth2 client credentials of the local Google application.
 *
 * @property clientId the client id of the Google application.
 * @property clientSecret the client secret of the Google application.
 * @property authUri the endpoint the user is sent to in order to authorise the application.
 * @property tokenUri the endpoint the authorisation codes and refresh tokens are exchanged at.
 * @property redirectUris the redirect URIs registered for the application.
 */
@Serializable
data class GoogleClientCredentials(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_secret")
    val clientSecret: String,
    @SerialName("auth_uri")
    val authUri: String = DEFAULT_AUTH_URI,
    @SerialName("token_uri")
    val tokenUri: String = DEFAULT_TOKEN_URI,
    @SerialName("redirect_uris")
    val redirectUris: List<String> = emptyList(),
) {
    companion object {
        /** Default authorisation endpoint of Google. */
        const val DEFAULT_AUTH_URI: String = "https://accounts.google.com/o/oauth2/auth"

        /** Default token endpoint of Google. */
        const val DEFAULT_TOKEN_URI: String = "https://oauth2.googleapis.com/token"

        private const val INSTALLED_KEY = "installed"
        private const val WEB_KEY = "web"

        /**
         * Loads the client credentials from a local file.
         *
         * Both the flat format and the `installed`/`web` format of the
         * `client_secrets.json` file that Google downloads are supported.
         *
         * @param path location of the credentials file.
         * @param json parser used to read the file.
         * @throws GmailAuthenticationException when the file is missing or cannot be parsed.
         */
        fun load(
            path: Path,
            json: Json = Json,
        ): GoogleClientCredentials {
            if (!Files.exists(path)) {
                throw GmailAuthenticationException("Credentials file not found: $path")
            }
            return try {
                val root = json.parseToJsonElement(Files.readString(path)).jsonObject
                val element = root[INSTALLED_KEY] ?: root[WEB_KEY] ?: root
                json.decodeFromJsonElement(serializer(), element)
            } catch (error: IllegalArgumentException) {
                throw GmailAuthenticationException(
                    "Credentials file $path could not be parsed: ${error.message}",
                    error,
                )
            }
        }
    }
}
