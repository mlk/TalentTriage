package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger

/**
 * Root command of the `recruiter-cli` executable.
 *
 * The root command only groups the available operations - every operation is a
 * subcommand of its own.
 *
 * @param logger sink for the structured log output of the invoked subcommand.
 */
class TriageCli(
    logger: StructuredLogger,
) : CliktCommand(name = COMMAND_NAME) {
    init {
        subcommands(ProcessInboxCommand(logger))
    }

    override fun run() {
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
