package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger

/**
 * Runs a single inbox triage pass.
 *
 * The command owns the command line contract and the lifecycle of a run. The
 * pipeline stages themselves - fetching the mail, screening it, extracting
 * the attachments and evaluating them - are delivered by their own stories
 * and plug into the run from there.
 *
 * @param logger sink for the structured log output of the run.
 */
class ProcessInboxCommand(
    private val logger: StructuredLogger,
) : SuspendingCliktCommand(name = COMMAND_NAME) {
    override suspend fun run() {
        logger.info(
            event = "$COMMAND_NAME.started",
            message = "Inbox triage run started",
            attributes = mapOf(TRIGGER_ATTRIBUTE to TRIGGER_CLI),
        )
        logger.info(
            event = "$COMMAND_NAME.completed",
            message = "Inbox triage run completed",
        )
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
