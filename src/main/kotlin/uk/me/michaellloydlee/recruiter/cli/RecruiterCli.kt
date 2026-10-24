package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.core.main
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger

/**
 * Entry point of the `recruiter-cli` executable.
 *
 * @param args command line arguments, the first one selects the subcommand.
 */
fun main(args: Array<String>) {
    TriageCli(StructuredLogger(System.out)).main(args)
}
