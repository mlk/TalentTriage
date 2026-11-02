package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.time.Duration
import java.util.UUID

/**
 * The local service that catches the redirect of the authorisation.
 *
 * The service listens on a free port of the loopback interface and answers the
 * redirect of Google: it reads the authorisation code of the query, exchanges
 * it for a token and stores the token. The browser gets a page that says what
 * happened.
 *
 * The service checks the state of the authorisation: a redirect that does not
 * carry the state of the service is refused, the redirect of another
 * application cannot authorise this one.
 *
 * @param authenticator exchanges the authorisation code for a token.
 * @param logger sink for the log of the service.
 * @param host the host of the loopback interface the service listens on.
 * @param timeout how long the service waits for the redirect of Google.
 */
class LocalAuthorizationService(
    private val authenticator: GmailAuthenticator,
    private val logger: StructuredLogger,
    private val host: String = DEFAULT_HOST,
    private val timeout: Duration = DEFAULT_TIMEOUT,
) {
    private var server: EmbeddedServer<*, *>? = null
    private val outcome = CompletableDeferred<AuthorizationOutcome>()
    private val answered = CompletableDeferred<Unit>()
    private val state: String = UUID.randomUUID().toString()
    private var port: Int = 0

    /**
     * Starts the service on a free port of the loopback interface.
     */
    suspend fun start() {
        val server =
            embeddedServer(CIO, port = 0, host = host) {
                routing {
                    get("/{...}") {
                        catchRedirect(call)
                    }
                }
            }
        server.start(wait = false)
        this.server = server
        port =
            server.engine
                .resolvedConnectors()
                .first()
                .port
        logger.info(
            event = EVENT_STARTED,
            message = "The local service waits for the redirect of the authorisation",
            attributes = mapOf("redirect_uri" to redirectUri()),
        )
    }

    /**
     * The redirect URI the service listens on.
     *
     * The port of the URI is the free port of the operating system: Google
     * does not check the port of the loopback redirect of a desktop client.
     */
    fun redirectUri(): String = "http://$host:$port"

    /**
     * The state the authorisation URL carries, the service refuses a redirect
     * that does not carry it.
     */
    fun state(): String = state

    /**
     * Waits for the redirect of Google and completes the authorisation.
     *
     * @return what the authorisation did.
     */
    suspend fun awaitAuthorization(): AuthorizationOutcome {
        val result =
            withTimeoutOrNull(timeout.toMillis()) { outcome.await() }
                ?: return AuthorizationOutcome.Failed("The authorisation did not arrive within $timeout")
        // The browser got its page, the service is not stopped before it.
        answered.await()
        return result
    }

    /**
     * Stops the service.
     */
    suspend fun stop() {
        server?.stopSuspend(GRACE_PERIOD_MILLIS, STOP_TIMEOUT_MILLIS)
        server = null
    }

    /**
     * The redirect of Google: the code is exchanged for a token and the
     * browser gets the page that says what happened.
     *
     * @param call the request of the browser.
     */
    private suspend fun catchRedirect(call: ApplicationCall) {
        val result = authorize(call.request.queryParameters)
        outcome.complete(result)
        try {
            call.respondText(pageOf(result), ContentType.Text.Html)
        } finally {
            answered.complete(Unit)
        }
    }

    /**
     * The authorisation of the redirect of Google.
     *
     * @param parameters the query of the redirect.
     * @return what the authorisation did.
     */
    private suspend fun authorize(parameters: Parameters): AuthorizationOutcome {
        val code = parameters[PARAM_CODE]
        val error = parameters[PARAM_ERROR]
        return when {
            parameters[PARAM_STATE] != state -> {
                logger.warn(
                    event = EVENT_REFUSED,
                    message = "The redirect of the authorisation does not carry the state of the service",
                    attributes = mapOf("state" to parameters[PARAM_STATE].orEmpty()),
                )
                AuthorizationOutcome.Failed("The redirect does not carry the state of the authorisation")
            }

            error != null -> {
                AuthorizationOutcome.Failed("The authorisation was refused: $error")
            }

            code == null -> {
                AuthorizationOutcome.Failed("The redirect of the authorisation carries no code")
            }

            else -> {
                exchange(code)
            }
        }
    }

    /**
     * The exchange of the authorisation code for a token.
     *
     * @param code the authorisation code of the redirect.
     * @return what the exchange did.
     */
    private suspend fun exchange(code: String): AuthorizationOutcome =
        try {
            AuthorizationOutcome.Authorised(authenticator.authenticate(code, redirectUri()))
        } catch (failure: OAuth2Exception) {
            AuthorizationOutcome.Failed("The authorisation code was refused: ${failure.message}")
        }

    /**
     * The page the browser gets.
     *
     * @param outcome what the authorisation did.
     * @return the page of the browser.
     */
    private fun pageOf(outcome: AuthorizationOutcome): String =
        when (outcome) {
            is AuthorizationOutcome.Authorised -> {
                page("The application is authorised", "You can close this tab.")
            }

            is AuthorizationOutcome.Failed -> {
                page("The authorisation failed", outcome.reason)
            }
        }

    /**
     * A page of the browser.
     *
     * @param title the title of the page.
     * @param message the message of the page.
     * @return the page.
     */
    private fun page(
        title: String,
        message: String,
    ): String =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="utf-8"><title>${escape(title)}</title></head>
        <body>
        <h1>${escape(title)}</h1>
        <p>${escape(message)}</p>
        </body>
        </html>
        """.trimIndent()

    /**
     * Escapes the text of a page, the message of Google ends up in it.
     *
     * @param text the text of the page.
     * @return the text, escaped.
     */
    private fun escape(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    companion object {
        /** The host of the loopback interface the service listens on. */
        const val DEFAULT_HOST: String = "localhost"

        /** How long the service waits for the redirect of Google. */
        val DEFAULT_TIMEOUT: Duration = Duration.ofMinutes(5)

        /** The event of the log of the service that waits for the redirect. */
        const val EVENT_STARTED: String = "authenticate.service_started"

        /** The event of the log of the service that refused a redirect. */
        const val EVENT_REFUSED: String = "authenticate.refused"

        private const val PARAM_CODE = "code"
        private const val PARAM_ERROR = "error"
        private const val PARAM_STATE = "state"
        private const val GRACE_PERIOD_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
