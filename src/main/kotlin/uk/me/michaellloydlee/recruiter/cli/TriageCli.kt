package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import io.ktor.client.HttpClient
import uk.me.michaellloydlee.recruiter.infrastructure.browser.openInBrowser
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailHttpClientFactory
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.time.Clock

/**
 * Root command of the `recruiter-cli` executable.
 *
 * The root command only groups the available operations - every operation is a
 * subcommand of its own.
 *
 * @param logger sink for the structured log output of the invoked subcommand.
 * @param httpClientFactory creates the client used to talk to Google.
 * @param clock source of the current time, the commands use it to decide how
 *   far back they look for messages.
 * @param environment the environment of the commands, it holds the API key of
 *   the web search of Ollama.
 * @param openBrowser opens the authorisation page of Google in the browser of
 *   the machine.
 */
class TriageCli(
    logger: StructuredLogger,
    httpClientFactory: () -> HttpClient = { GmailHttpClientFactory.create() },
    clock: Clock = Clock.systemUTC(),
    environment: Map<String, String> = System.getenv(),
    openBrowser: suspend (String) -> Unit = { url -> openInBrowser(url) },
) : SuspendingCliktCommand(name = COMMAND_NAME) {
    init {
        subcommands(
            AuthenticateCommand(logger, httpClientFactory, openBrowser),
            PendingCommand(logger, httpClientFactory, clock),
            ProcessInboxCommand(logger, httpClientFactory, clock),
            RequirementsCommand(logger, httpClientFactory, environment),
        )
    }

    override suspend fun run() {
        // The root command only groups the subcommands, it has no work of its own.
        // Without a subcommand Clikt prints the help message.
    }

    override fun help(context: Context): String =
        "TalentTriage - a local-first recruiter email triage engine for macOS."

    companion object {
        /** Name of the executable, also used as the Gradle application name. */
        const val COMMAND_NAME: String = "recruiter-cli"
    }
}
