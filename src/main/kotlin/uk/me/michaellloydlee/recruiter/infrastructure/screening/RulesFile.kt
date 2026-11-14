package uk.me.michaellloydlee.recruiter.infrastructure.screening

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.domain.screening.JobListingHeuristics
import uk.me.michaellloydlee.recruiter.domain.screening.RecruiterHeuristics
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The rules of the screening.
 *
 * Every rule the job seeker does not write in the file is the rule of the
 * code, a rule that is in the file replaces the rule of the code.
 */
data class ScreeningRules(
    val recruiter: RecruiterHeuristics,
    val jobListings: JobListingHeuristics,
) {
    companion object {
        /**
         * The rules of the code.
         */
        fun defaults(): ScreeningRules =
            ScreeningRules(
                recruiter =
                    RecruiterHeuristics(
                        keywords = RecruiterHeuristics.DEFAULT_KEYWORDS,
                        senderHints = RecruiterHeuristics.DEFAULT_SENDER_HINTS,
                    ),
                jobListings =
                    JobListingHeuristics(
                        senderHints = JobListingHeuristics.DEFAULT_SENDER_HINTS,
                        phrases = JobListingHeuristics.DEFAULT_PHRASES,
                    ),
            )
    }
}

/**
 * Reads the rules of the screening from a local JSON file.
 *
 * The file holds the words a recruiter mail talks about, the parts of the
 * sender address of a recruiter mail, the parts of the sender address of a job
 * board and the phrases of a job listing:
 *
 * ```json
 * {
 *   "recruiter_keywords": ["stellenangebot", "ingenieur"],
 *   "recruiter_sender_hints": ["personalberatung"],
 *   "job_board_senders": ["s1jobs.example"],
 *   "job_listing_phrases": ["offres d'emploi"]
 * }
 * ```
 *
 * A field that is not in the file keeps the rule of the code, a field that is
 * in the file replaces the list of the code, an empty list empties it. A key
 * that is not one of the four fields is an error, so a typo in the file does
 * not silently keep the rule of the code. An empty file is an error too.
 */
object RulesFile {
    /**
     * Loads the rules of the screening.
     *
     * @param path location of the file.
     * @param json parser used to read the file.
     * @return the rules of the screening, the fields the file does not hold are the rules of the code.
     * @throws IllegalArgumentException when the file is missing, cannot be read or holds a blank word.
     */
    fun load(
        path: Path,
        json: Json = Json { ignoreUnknownKeys = false },
    ): ScreeningRules {
        require(Files.exists(path)) {
            "Rules file not found: $path"
        }
        val file = decode(path, read(path), json)
        validate(path, file)
        return ScreeningRules(
            recruiter =
                RecruiterHeuristics(
                    keywords = file.recruiterKeywords ?: RecruiterHeuristics.DEFAULT_KEYWORDS,
                    senderHints = file.recruiterSenderHints ?: RecruiterHeuristics.DEFAULT_SENDER_HINTS,
                ),
            jobListings =
                JobListingHeuristics(
                    senderHints = file.jobBoardSenders ?: JobListingHeuristics.DEFAULT_SENDER_HINTS,
                    phrases = file.jobListingPhrases ?: JobListingHeuristics.DEFAULT_PHRASES,
                ),
        )
    }

    private fun read(path: Path): String {
        val text =
            try {
                Files.readString(path)
            } catch (error: IOException) {
                throw IllegalArgumentException("Rules file $path could not be read: ${error.message}", error)
            }
        require(text.isNotBlank()) {
            "Rules file $path is empty"
        }
        return text
    }

    private fun decode(
        path: Path,
        text: String,
        json: Json,
    ): Rules =
        try {
            json.decodeFromString(Rules.serializer(), text)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Rules file $path could not be read: ${error.message}", error)
        }

    private fun validate(
        path: Path,
        file: Rules,
    ) {
        listOf(
            "recruiter_keywords" to file.recruiterKeywords,
            "recruiter_sender_hints" to file.recruiterSenderHints,
            "job_board_senders" to file.jobBoardSenders,
            "job_listing_phrases" to file.jobListingPhrases,
        ).forEach { (name, words) ->
            require(words == null || words.none { it.isBlank() }) {
                "Rules file $path holds a blank word in $name"
            }
        }
    }

    @Serializable
    private data class Rules(
        @SerialName("recruiter_keywords")
        val recruiterKeywords: List<String>? = null,
        @SerialName("recruiter_sender_hints")
        val recruiterSenderHints: List<String>? = null,
        @SerialName("job_board_senders")
        val jobBoardSenders: List<String>? = null,
        @SerialName("job_listing_phrases")
        val jobListingPhrases: List<String>? = null,
    )
}
