package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import io.ktor.client.HttpClient
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FileOAuth2TokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailAuthenticationException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailAuthenticator
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailHttpClientFactory
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailOAuth2Client
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GoogleClientCredentials
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Exception
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.nio.file.Path
import java.util.UUID

/**
 * Authorises the application against the Gmail API.
 *
 * Without an authorisation code the command prints the URL the user has to
 * open, with the code it exchanges the code for a token and stores it.
 *
 * @param logger sink for the structured log output.
 * @param httpClientFactory creates the client used for the token requests.
 */
class AuthenticateCommand(
    private val logger: StructuredLogger,
    private val httpClientFactory: () -> HttpClient = { GmailHttpClientFactory.create() },
) : SuspendingCliktCommand(name = COMMAND_NAME) {
    private val code: String? by
        argument(
            "CODE",
            help = "The authorisation code Google redirects to, taken from the address bar",
        ).optional()

    private val credentialsFile: String by
        option(
            "--credentials",
            "-c",
            help = "The file with the OAuth2 client credentials",
        ).default(DEFAULT_CREDENTIALS_FILE)

    private val tokenFile: String by
        option(
            "--token",
            "-t",
            help = "The file the OAuth2 token is stored in",
        ).default(DEFAULT_TOKEN_FILE)

    private val redirectUri: String by
        option(
            "--redirect-uri",
            help = "The redirect URI registered for the Google application",
        ).default(DEFAULT_REDIRECT_URI)

    private val scopes: List<String> by
        option(
            "--scope",
            "-s",
            help = "The OAuth2 scopes to request, repeatable",
        ).multiple()

    override suspend fun run() {
        val credentials = loadCredentials()
        val requestedScopes = scopes.ifEmpty { listOf(DEFAULT_SCOPE) }
        httpClientFactory().use { client ->
            val authenticator =
                GmailAuthenticator(
                    oauth2Client = GmailOAuth2Client(client, credentials),
                    tokenStore = FileOAuth2TokenStore(Path.of(tokenFile)),
                )
            val authorizationCode = code
            if (authorizationCode == null) {
                printAuthorizationUrl(authenticator, requestedScopes)
                return@use
            }
            val token =
                try {
                    authenticator.authenticate(authorizationCode, redirectUri)
                } catch (error: OAuth2Exception) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                }
            logger.info(
                event = "$COMMAND_NAME.completed",
                message = "The Gmail API is authorised",
                attributes =
                    mapOf(
                        "token_file" to tokenFile,
                        "expires_at" to token.expiresAt.toString(),
                        "scope" to token.scope.orEmpty(),
                    ),
            )
        }
    }

    private fun loadCredentials(): GoogleClientCredentials =
        try {
            GoogleClientCredentials.load(Path.of(credentialsFile))
        } catch (error: GmailAuthenticationException) {
            throw CommandFailedException(error.message.orEmpty(), error)
        }

    private fun printAuthorizationUrl(
        authenticator: GmailAuthenticator,
        requestedScopes: List<String>,
    ) {
        val url = authenticator.authorizationUrl(redirectUri, requestedScopes, UUID.randomUUID().toString())
        echo(
            "Open the following URL in a browser, authorise the application and copy the " +
                "authorisation code from the address bar of the redirect:",
            err = true,
        )
        echo(url, err = true)
        logger.info(
            event = "$COMMAND_NAME.authorisation_required",
            message = "The application has to be authorised in a browser first",
            attributes =
                mapOf(
                    "url" to url,
                    "redirect_uri" to redirectUri,
                    "scope" to requestedScopes.joinToString(separator = " "),
                ),
        )
    }

    override fun help(context: Context): String =
        "Authorise the application against the Gmail API and store the token locally."

    companion object {
        /** Name of the subcommand. */
        const val COMMAND_NAME: String = "authenticate"

        /** The file the OAuth2 client credentials are read from by default. */
        const val DEFAULT_CREDENTIALS_FILE: String = "credentials.json"

        /** The file the OAuth2 token is stored in by default. */
        const val DEFAULT_TOKEN_FILE: String = "token.json"

        /** The redirect URI of a locally installed Google application. */
        const val DEFAULT_REDIRECT_URI: String = "http://localhost"

        /** The scope needed to read and modify the mailbox. */
        const val DEFAULT_SCOPE: String = "https://www.googleapis.com/auth/gmail.modify"
    }
}
