package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import io.ktor.client.HttpClient
import uk.me.michaellloydlee.recruiter.application.ProcessInboxService
import uk.me.michaellloydlee.recruiter.application.screening.LocalLlmClassifier
import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.screening.RecruiterHeuristics
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.FileOAuth2TokenStore
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailAuthenticationException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailHttpClientFactory
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailServices
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GoogleClientCredentials
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.nio.file.Path

/**
 * Runs a single inbox triage pass.
 *
 * The command fetches the messages of the inbox that were not processed yet
 * and screens them: the ones that are not recruiter mails are filtered out
 * with the label `Recruiters/FilteredOut`. The deep evaluation of the
 * attachments of the recruiter mails comes with its own story.
 *
 * @param logger sink for the structured log output of the run.
 * @param httpClientFactory creates the client used to talk to Google and to
 *   the local model. It is called once per invocation and the client is closed
 *   when the command is done, so the factory must return a fresh client every
 *   time.
 */
class ProcessInboxCommand(
    private val logger: StructuredLogger,
    private val httpClientFactory: () -> HttpClient = { GmailHttpClientFactory.create() },
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

    private val model: String by
        option(
            "--model",
            "-m",
            help = "The local model that screens the messages",
        ).default(LocalLlmClassifier.DEFAULT_MODEL)

    override suspend fun run() {
        logger.info(
            event = "$COMMAND_NAME.started",
            message = "Inbox triage run started",
            attributes = mapOf(TRIGGER_ATTRIBUTE to TRIGGER_CLI),
        )
        val credentials =
            try {
                GoogleClientCredentials.load(Path.of(credentialsFile))
            } catch (error: GmailAuthenticationException) {
                throw CommandFailedException(error.message.orEmpty(), error)
            }
        httpClientFactory().use { client ->
            val gmail =
                GmailServices.create(
                    credentials = credentials,
                    tokenStore = FileOAuth2TokenStore(Path.of(tokenFile)),
                    httpClient = client,
                )
            val screening =
                ScreeningService(
                    heuristics = RecruiterHeuristics(),
                    classifier = LocalLlmClassifier(OllamaChatClient(client), model = model),
                    stateManager = gmail.stateManager,
                )
            val summary =
                try {
                    ProcessInboxService(gmail.messages, screening, logger).run()
                } catch (error: GmailApiException) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                } catch (error: OllamaException) {
                    throw CommandFailedException(error.message.orEmpty(), error)
                }
            logger.info(
                event = "$COMMAND_NAME.completed",
                message = "Inbox triage run completed",
                attributes =
                    mapOf(
                        "screened" to summary.screened.toString(),
                        "recruiters" to summary.recruiters.toString(),
                        "filtered_out" to summary.filteredOut.toString(),
                        "unknown" to summary.unknown.toString(),
                    ),
            )
        }
    }

    override fun help(context: Context): String = "Fetch, triage and label the unprocessed messages of the inbox."

    companion object {
        /** Name of the subcommand. */
        const val COMMAND_NAME: String = "process-inbox"

        /** Name of the attribute that records what triggered the run. */
        const val TRIGGER_ATTRIBUTE: String = "trigger"

        /** Value of the trigger attribute for a run started from the command line. */
        const val TRIGGER_CLI: String = "cli"
    }
}
