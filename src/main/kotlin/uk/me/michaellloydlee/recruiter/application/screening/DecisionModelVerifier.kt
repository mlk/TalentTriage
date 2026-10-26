package uk.me.michaellloydlee.recruiter.application.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
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
            val response =
                client.decide(
                    OllamaDecisionRequest(
                        model = model,
                        state = state(message),
                        questions = mapOf(QUESTION to question()),
                    ),
                )
            verdict(response.answers[QUESTION])
        } catch (error: OllamaException) {
            RecruiterVerification.Unknown("The decision model answered with an error: ${error.message}")
        } catch (error: IOException) {
            RecruiterVerification.Unknown("The decision model could not be reached: ${error.message}")
        }

    private fun verdict(
        answer: uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionAnswer?,
    ): RecruiterVerification {
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
                RecruiterVerification.NotRecruiter
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

    private fun question(): OllamaDecisionQuestion =
        OllamaDecisionQuestion(
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

        /** The answer of a message that is from a recruiter. */
        const val ANSWER_FROM_A_RECRUITER: String = "from a recruiter"

        /** The answer of a message that is not from a recruiter. */
        const val ANSWER_NOT_FROM_A_RECRUITER: String = "not from a recruiter"

        /** The answer of a message the model cannot tell. */
        const val ANSWER_UNKNOWN: String = "unknown"

        private const val INSTRUCTIONS =
            "Which of the options fits the message? Answer with the option only."
    }
}
