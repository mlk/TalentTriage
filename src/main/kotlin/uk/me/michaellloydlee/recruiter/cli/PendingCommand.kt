package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.client.HttpClient
import uk.me.michaellloydlee.recruiter.domain.gmail.MessageWindow
import uk.me.michaellloydlee.recruiter.domain.gmail.messageWindow
import uk.me.michaellloydlee.recruiter.domain.gmail.runWindow
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FileOAuth2TokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailAuthenticationException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailHttpClientFactory
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailServices
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GoogleClientCredentials
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.run.FileLastRunStore
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

/**
 * Lists the inbox messages that were not processed yet.
 *
 * The messages are the ones without a state label, they are the input of the
 * triage pipeline.
 *
 * @param logger sink for the structured log output.
 * @param httpClientFactory creates the client used to talk to Google. It is
 *   called once per invocation and the client is closed when the command is
 *   done, so the factory must return a fresh client every time.
 * @param clock source of the current time, it decides how far back the
 *   command looks for messages.
 */
class PendingCommand(
    private val logger: StructuredLogger,
    private val httpClientFactory: () -> HttpClient = { GmailHttpClientFactory.create() },
    private val clock: Clock = Clock.systemUTC(),
) : SuspendingCliktCommand(name = COMMAND_NAME) {
    private val credentialsFile: String by
        option(
            "--credentials",
            "-c",
            help = "The file with the OAuth2 client credentials",
        ).default(AuthenticateCommand.DEFAULT_CREDENTIALS_FILE)

    private val tokenFile: String by
        option(
            "--token",
            "-t",
            help = "The file the OAuth2 token is stored in",
        ).default(AuthenticateCommand.DEFAULT_TOKEN_FILE)

    private val since: String? by
        option(
            "--since",
            help =
                "How far back the messages are looked for, an ISO-8601 duration like PT24H or P1D. " +
                    "The default is the gap since the last successful run",
        )

    private val limit: Int by
        option(
            "--limit",
            "-l",
            help = "The number of messages that are shown, every message is fetched from the API",
        ).int().default(DEFAULT_LIMIT)

    override suspend fun run() {
        val credentials =
            try {
                GoogleClientCredentials.load(Path.of(credentialsFile))
            } catch (error: GmailAuthenticationException) {
                throw CommandFailedException(error.message.orEmpty(), error)
            }
        httpClientFactory().use { client ->
            val services =
                GmailServices.create(
                    credentials = credentials,
                    tokenStore = FileOAuth2TokenStore(Path.of(tokenFile)),
                    httpClient = client,
                    clock = clock,
                )
            val window = window(clock.instant())
            val summaries =
                try {
                    services.messages.listUnprocessed(window)
                } catch (error: GmailApiException) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                }
            logger.info(
                event = "$COMMAND_NAME.listed",
                message = "The inbox messages that were not processed yet",
                attributes = mapOf("count" to summaries.size.toString()),
            )
            summaries.take(limit).forEach { summary ->
                val message = services.messages.getMessage(summary.id)
                logger.info(
                    event = "$COMMAND_NAME.message",
                    message = "A message of the inbox that was not processed yet",
                    attributes =
                        mapOf(
                            "id" to message.id,
                            "sender" to message.sender,
                            "subject" to message.subject,
                        ),
                )
            }
        }
    }

    /**
     * The window of the command, the messages that arrived before it are not
     * looked at.
     *
     * The window is the gap since the last successful run, a window on the
     * command line wins over it.
     *
     * @param now the moment of the command.
     */
    private suspend fun window(now: Instant): MessageWindow =
        try {
            runWindow(
                lastRun = lastRunStore().load(),
                now = now,
                given = since?.let { messageWindow(it) },
            )
        } catch (error: IllegalArgumentException) {
            throw CommandFailedException(error.message.orEmpty(), error)
        }

    /**
     * The store of the moment of the last successful run, next to the token.
     */
    private fun lastRunStore(): FileLastRunStore = FileLastRunStore.nextTo(Path.of(tokenFile))

    override fun help(context: Context): String = "List the inbox messages that were not processed yet."

    companion object {
        /** Name of the subcommand. */
        const val COMMAND_NAME: String = "pending"

        /** The number of messages the command shows by default. */
        const val DEFAULT_LIMIT: Int = 20
    }
}
