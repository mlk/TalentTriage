package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import kotlinx.coroutines.runBlocking
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import kotlin.system.exitProcess

/**
 * Entry point of the `recruiter-cli` executable.
 *
 * @param args command line arguments, the first one selects the subcommand.
 */
fun main(args: Array<String>) {
    val logger = StructuredLogger(System.out)
    val code = runBlocking { runCli(args, logger, TriageCli(logger)) }
    if (code != 0) {
        exitProcess(code)
    }
}

/**
 * Runs the command line and reports a failure of the run as a log record.
 *
 * A failure that nobody expected is a record of the log and not a stack trace
 * on standard error: the log of a run is one JSON object per line, and the
 * record holds the stack trace in its message.
 *
 * The errors of the command line itself - an unknown option, the help - are
 * printed by the command line parser, they are for the human at the terminal.
 *
 * The entry point is the last line of defence of the pipeline, it has to
 * survive anything the pipeline throws at it. The exception is not hidden, it
 * is logged with its stack trace.
 *
 * @param args the command line arguments.
 * @param logger the sink of the log records.
 * @param command the command that is run.
 * @return the status code of the run.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun runCli(
    args: Array<String>,
    logger: StructuredLogger,
    command: CoreSuspendingCliktCommand,
): Int =
    try {
        command.main(args)
        0
    } catch (error: Exception) {
        logger.logFailure(error)
        1
    }
