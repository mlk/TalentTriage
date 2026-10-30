package uk.me.michaellloydlee.recruiter.infrastructure.run

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * The moment of the last successful run of the pipeline.
 *
 * The moment is kept next to the token of the application, so it survives a
 * restart of the machine. It is written after a successful run only: a run
 * that failed looks back further, the mail of the failed run is picked up
 * again.
 *
 * @param path location of the file the moment is kept in.
 */
class FileLastRunStore(
    private val path: Path,
) {
    /**
     * Reads the moment of the last successful run.
     *
     * @return the moment, or `null` when no run succeeded yet or when the file
     *   does not hold a moment.
     */
    suspend fun load(): Instant? =
        withContext(Dispatchers.IO) {
            if (!Files.exists(path)) {
                null
            } else {
                momentOf(Files.readString(path))
            }
        }

    /**
     * Writes the moment of the last successful run.
     *
     * @param moment the moment of the run.
     */
    suspend fun save(moment: Instant) {
        withContext(Dispatchers.IO) {
            Files.createDirectories(path.toAbsolutePath().parent)
            Files.writeString(path, moment.toString())
        }
    }

    /**
     * The moment in the text of the file.
     *
     * A file that does not hold a moment is a file that cannot be used: the
     * run looks back as far as the default window and the file is replaced by
     * the next run.
     *
     * @param text the content of the file.
     * @return the moment, or `null` when the text is not a moment.
     */
    private fun momentOf(text: String): Instant? =
        try {
            Instant.parse(text.trim())
        } catch (error: DateTimeParseException) {
            null
        }

    companion object {
        /**
         * The store of the moment of the last run, next to the token file.
         *
         * @param tokenFile location of the token file.
         */
        fun nextTo(tokenFile: Path): FileLastRunStore =
            FileLastRunStore(tokenFile.resolveSibling("${tokenFile.fileName}.last-run"))
    }
}
