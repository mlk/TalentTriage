package uk.me.michaellloydlee.recruiter.application.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionAnswer
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionQuestion
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionRequest
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import java.io.IOException

/**
 * Verifies a message with a decision model of Ollama.
 *
 * The model answers one `choice` question with the three options `from a
 * recruiter`, `not from a recruiter` and `unknown`, and tells how likely every
 * one of them is. A model that is not sure about a mail leaves it alone.
 *
 * The rules of the job listing decide a listing without the model, the model
 * is the fallback for the messages the rules do not catch: a message the model
 * does not see as a recruiter mail is asked about once more, whether it is a
 * job listing. The second question only refines the state of the message, a
 * model that is not sure about it filters the message out.
 *
 * @param client the client of the decision API.
 * @param model the name of the decision model.
 * @param minimumConfidence how sure the model has to be, a model that is less
 *   sure leaves the message alone.
 * @param maxBodyLength how many characters of the body go into the state.
 */
class DecisionModelVerifier(
    private val client: OllamaDecisionClient,
    private val model: String = DEFAULT_MODEL,
    private val minimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
    private val maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
) : RecruiterVerifier {
    override suspend fun verify(message: EmailMessage): RecruiterVerification =
        try {
            val response = client.decide(request(message, QUESTION, recruiterQuestion()))
            val verdict = verdict(response.answers[QUESTION])
            if (verdict is RecruiterVerification.NotRecruiter) {
                jobListing(message)
            } else {
                verdict
            }
        } catch (error: OllamaException) {
            RecruiterVerification.Unknown("The decision model answered with an error: ${error.message}")
        } catch (error: IOException) {
            RecruiterVerification.Unknown("The decision model could not be reached: ${error.message}")
        }

    /**
     * The fallback of the rules: the model tells whether the message is a job
     * listing.
     *
     * A model that is not sure about it filters the message out: the message
     * is not a recruiter mail, that is what was decided before, and the
     * question only refines that state.
     */
    private suspend fun jobListing(message: EmailMessage): RecruiterVerification =
        try {
            val response = client.decide(request(message, JOB_LISTING_QUESTION, jobListingQuestion()))
            listingVerdict(response.answers[JOB_LISTING_QUESTION])
        } catch (error: OllamaException) {
            RecruiterVerification.NotRecruiter(
                "The decision model answered with an error: ${error.message}, the message is filtered out",
            )
        } catch (error: IOException) {
            RecruiterVerification.NotRecruiter(
                "The decision model could not be reached: ${error.message}, the message is filtered out",
            )
        }

    private fun verdict(answer: OllamaDecisionAnswer?): RecruiterVerification {
        if (answer == null) {
            return RecruiterVerification.Unknown("The decision model did not answer the question")
        }
        val confidence = answer.confidence ?: 0.0
        if (confidence < minimumConfidence) {
            return RecruiterVerification.Unknown(
                "The decision model is not sure enough (confidence $confidence)",
            )
        }
        return when (answer.choice) {
            ANSWER_FROM_A_RECRUITER -> {
                RecruiterVerification.Recruiter
            }

            ANSWER_NOT_FROM_A_RECRUITER -> {
                RecruiterVerification.NotRecruiter("The model does not see a recruiter mail in it")
            }

            ANSWER_UNKNOWN -> {
                RecruiterVerification.Unknown("The decision model was not sure")
            }

            else -> {
                RecruiterVerification.Unknown(
                    "The decision model answered with '${answer.choice}'",
                )
            }
        }
    }

    /**
     * The verdict of the question about the job listing.
     */
    private fun listingVerdict(answer: OllamaDecisionAnswer?): RecruiterVerification {
        if (answer == null) {
            return RecruiterVerification.NotRecruiter(
                "The decision model did not answer the question about the job listing, " +
                    "the message is filtered out",
            )
        }
        val confidence = answer.confidence ?: 0.0
        if (confidence < minimumConfidence) {
            return RecruiterVerification.NotRecruiter(
                "The decision model is not sure about the job listing (confidence $confidence), " +
                    "the message is filtered out",
            )
        }
        return when (answer.choice) {
            ANSWER_A_JOB_LISTING -> {
                RecruiterVerification.JobListing
            }

            else -> {
                RecruiterVerification.NotRecruiter(
                    "The model does not see a recruiter mail or a job listing in it",
                )
            }
        }
    }

    private fun request(
        message: EmailMessage,
        question: String,
        asked: OllamaDecisionQuestion,
    ): OllamaDecisionRequest =
        OllamaDecisionRequest(
            model = model,
            state = state(message),
            questions = mapOf(question to asked),
        )

    private fun recruiterQuestion(): OllamaDecisionQuestion =
        OllamaDecisionQuestion(
            type = OllamaDecisionQuestion.TYPE_CHOICE,
            instructions = INSTRUCTIONS,
            criteria =
                mapOf(
                    ANSWER_FROM_A_RECRUITER to
                        "The message is from a recruiter or an employer who is looking for candidates for a position",
                    ANSWER_NOT_FROM_A_RECRUITER to
                        "The message is not about a position, a job application, an interview or a job offer",
                    ANSWER_UNKNOWN to "The message is too short, too unclear or too mixed to tell",
                ),
        )

    /**
     * The question of the fallback: is the message a job listing?
     */
    private fun jobListingQuestion(): OllamaDecisionQuestion =
        OllamaDecisionQuestion(
            type = OllamaDecisionQuestion.TYPE_CHOICE,
            instructions = INSTRUCTIONS,
            criteria =
                mapOf(
                    ANSWER_A_JOB_LISTING to
                        "The message is an alert of a job board, it offers many jobs at once",
                    ANSWER_NOT_A_JOB_LISTING to
                        "The message is about one job, one position or one person",
                ),
        )

    private fun state(message: EmailMessage): String =
        """
        Subject: ${message.subject}

        Body:
        ${message.body.take(maxBodyLength)}
        """.trimIndent()

    companion object {
        /** The decision model of the recruiter verification. */
        const val DEFAULT_MODEL: String = "clef:cloud"

        /** How sure the model has to be, a model that is less sure leaves the message alone. */
        const val DEFAULT_MINIMUM_CONFIDENCE: Double = 0.5

        /** How many characters of the body go into the state by default. */
        const val DEFAULT_MAX_BODY_LENGTH: Int = 4_000

        /** The name of the question of the recruiter verification. */
        const val QUESTION: String = "recruiter"

        /** The name of the question of the job listing. */
        const val JOB_LISTING_QUESTION: String = "job_listing"

        /** The answer of a message that is from a recruiter. */
        const val ANSWER_FROM_A_RECRUITER: String = "from a recruiter"

        /** The answer of a message that is not from a recruiter. */
        const val ANSWER_NOT_FROM_A_RECRUITER: String = "not from a recruiter"

        /** The answer of a message the model cannot tell. */
        const val ANSWER_UNKNOWN: String = "unknown"

        /** The answer of a message that is a job listing. */
        const val ANSWER_A_JOB_LISTING: String = "a job listing"

        /** The answer of a message that is not a job listing. */
        const val ANSWER_NOT_A_JOB_LISTING: String = "not a job listing"

        private const val INSTRUCTIONS =
            "Which of the options fits the message? Answer with the option only."
    }
}
