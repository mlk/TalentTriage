package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import io.ktor.client.HttpClient
import uk.me.michaellloydlee.recruiter.infrastructure.browser.openInBrowser
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.AuthorizationOutcome
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FileOAuth2TokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailAuthenticationException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailAuthenticator
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailHttpClientFactory
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailOAuth2Client
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GoogleClientCredentials
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.LocalAuthorizationService
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Exception
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.OAuth2Token
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

/**
 * Authorises the application against the Gmail API.
 *
 * Without an authorisation code the command starts a local service, opens the
 * authorisation page of Google in the browser and waits for the redirect: the
 * service catches the code, exchanges it for a token and stores it. With the
 * code the command exchanges it for a token and stores it, the code was
 * copied out of the address bar of the browser.
 *
 * @param logger sink for the structured log output.
 * @param httpClientFactory creates the client used for the token requests. It
 *   is called once per invocation and the client is closed when the command is
 *   done, so the factory must return a fresh client every time.
 * @param openBrowser opens the authorisation page of Google in the browser of
 *   the machine.
 */
class AuthenticateCommand(
    private val logger: StructuredLogger,
    private val httpClientFactory: () -> HttpClient = { GmailHttpClientFactory.create() },
    private val openBrowser: suspend (String) -> Unit = { url -> openInBrowser(url) },
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
            help =
                "The redirect URI registered for the Google application. The local service " +
                    "listens on its host, the port of the service is a free one",
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
                authorize(authenticator, requestedScopes)
                return@use
            }
            val token =
                try {
                    authenticator.authenticate(authorizationCode, redirectUri)
                } catch (error: OAuth2Exception) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                }
            logAuthorised(token)
        }
    }

    /**
     * The authorisation in the browser: the local service catches the redirect
     * of Google, the code is exchanged for a token and the token is stored.
     *
     * @param authenticator exchanges the authorisation code for a token.
     * @param requestedScopes the scopes the authorisation asks for.
     */
    private suspend fun authorize(
        authenticator: GmailAuthenticator,
        requestedScopes: List<String>,
    ) {
        val service =
            LocalAuthorizationService(
                authenticator = authenticator,
                logger = logger,
                host = hostOf(redirectUri),
            )
        try {
            service.start()
            val url =
                authenticator.authorizationUrl(
                    redirectUri = service.redirectUri(),
                    scopes = requestedScopes,
                    state = service.state(),
                )
            echo(
                "The browser opens the authorisation page of Google, allow the application and the " +
                    "command continues:",
                err = true,
            )
            echo(url, err = true)
            logger.info(
                event = "$COMMAND_NAME.authorisation_required",
                message = "The application has to be authorised in a browser first",
                attributes =
                    mapOf(
                        "url" to url,
                        "redirect_uri" to service.redirectUri(),
                        "scope" to requestedScopes.joinToString(separator = " "),
                    ),
            )
            openBrowser(url)
            when (val outcome = service.awaitAuthorization()) {
                is AuthorizationOutcome.Authorised -> logAuthorised(outcome.token)
                is AuthorizationOutcome.Failed -> throw CommandFailedException(outcome.reason)
            }
        } finally {
            service.stop()
        }
    }

    /**
     * The host of the redirect URI, the local service listens on it.
     *
     * @param uri the redirect URI of the command line.
     * @return the host of the redirect URI.
     */
    private fun hostOf(uri: String): String {
        val host =
            try {
                URI(uri).host
            } catch (error: URISyntaxException) {
                throw CommandFailedException("'$uri' is not a URI", error)
            }
        return host ?: throw CommandFailedException("'$uri' has no host, the local service needs one")
    }

    /**
     * The log of an authorisation that worked.
     *
     * @param token the token that was stored.
     */
    private fun logAuthorised(token: OAuth2Token) {
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

    private fun loadCredentials(): GoogleClientCredentials =
        try {
            GoogleClientCredentials.load(Path.of(credentialsFile))
        } catch (error: GmailAuthenticationException) {
            throw CommandFailedException(error.message.orEmpty(), error)
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
