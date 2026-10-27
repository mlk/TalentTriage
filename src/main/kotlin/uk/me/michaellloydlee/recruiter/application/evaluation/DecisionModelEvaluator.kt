package uk.me.michaellloydlee.recruiter.application.evaluation

import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionAnswer
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionQuestion
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionRequest
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import java.io.IOException

/**
 * Evaluates a job description with a decision model of Ollama.
 *
 * Every criterion of the evaluation is one `choice` question with the options
 * `matches` and `does not match`, and the model answers all of them in a
 * single request. The model also tells how sure it is, a model that is not
 * sure enough leaves the mail alone.
 *
 * @param client the client of the decision API.
 * @param model the name of the decision model.
 * @param minimumConfidence how sure the model has to be, a model that is less
 *   sure leaves the mail alone.
 * @param maxBodyLength how many characters of the text go into the state.
 */
class DecisionModelEvaluator(
    private val client: OllamaDecisionClient,
    private val model: String = DEFAULT_MODEL,
    private val minimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
    private val maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
) : JobDescriptionEvaluator {
    override suspend fun evaluate(
        message: EmailMessage,
        attachments: String,
        requirements: JobRequirements,
    ): JobDescriptionEvaluation =
        try {
            val response =
                client.decide(
                    OllamaDecisionRequest(
                        model = model,
                        state = evaluationText(message, attachments).take(maxBodyLength),
                        questions = questions(requirements),
                    ),
                )
            evaluation(response.answers)
        } catch (error: OllamaException) {
            JobDescriptionEvaluation.Unknown("The decision model answered with an error: ${error.message}")
        } catch (error: IOException) {
            JobDescriptionEvaluation.Unknown("The decision model could not be reached: ${error.message}")
        }

    private fun evaluation(answers: Map<String, OllamaDecisionAnswer>): JobDescriptionEvaluation {
        val criteria = mutableMapOf<String, Boolean>()
        answers.forEach { (name, answer) ->
            val confidence = answer.confidence
            if (confidence != null && confidence < minimumConfidence) {
                return JobDescriptionEvaluation.Unknown(
                    "The decision model is not sure enough about $name (confidence $confidence)",
                )
            }
            criteria[name] = answer.choice == OPTION_MATCHES
        }
        val jdMatches = criteria[JD_MATCHES]
        val officeDaysMatch = criteria[OFFICE_DAYS_MATCH]
        if (jdMatches == null || officeDaysMatch == null) {
            return JobDescriptionEvaluation.Unknown(MISSING_ANSWER)
        }
        return JobDescriptionEvaluation.Evaluated(
            payMatches = criteria[PAY_MATCHES] ?: true,
            jdMatches = jdMatches,
            officeDaysMatch = officeDaysMatch,
            payMissing = criteria[PAY_MISSING] ?: false,
        )
    }

    private fun questions(requirements: JobRequirements): Map<String, OllamaDecisionQuestion> {
        val questions =
            mutableMapOf(
                JD_MATCHES to
                    question(
                        instructions =
                            "Does the job description match the profile of the job seeker: " +
                                requirements.profile,
                        matches = "The job description matches the profile of the job seeker",
                        doesNotMatch = "The job description does not match the profile of the job seeker",
                    ),
                OFFICE_DAYS_MATCH to
                    question(
                        instructions =
                            "Does the job demand more than ${requirements.maximumOfficeDays} days of " +
                                "office per week? A job that says nothing about the office days " +
                                "demands none.",
                        matches =
                            "The job demands at most ${requirements.maximumOfficeDays} days of office per week",
                        doesNotMatch =
                            "The job demands more than ${requirements.maximumOfficeDays} days of office per week",
                    ),
            )
        val pay = requirements.minimumPay
        if (pay != null) {
            questions[PAY_MATCHES] =
                question(
                    instructions =
                        "Does the message state a pay of at least ${pay.description}?",
                    matches = "The message states a pay of at least ${pay.description}",
                    doesNotMatch = "The message states no pay of at least ${pay.description}",
                )
            questions[PAY_MISSING] =
                question(
                    instructions = "Is the pay missing from the message?",
                    matches = "The message states no pay at all",
                    doesNotMatch = "The message states a pay",
                )
        }
        return questions
    }

    private fun question(
        instructions: String,
        matches: String,
        doesNotMatch: String,
    ): OllamaDecisionQuestion =
        OllamaDecisionQuestion(
            type = OllamaDecisionQuestion.TYPE_CHOICE,
            instructions = instructions,
            criteria =
                mapOf(
                    OPTION_MATCHES to matches,
                    OPTION_DOES_NOT_MATCH to doesNotMatch,
                ),
        )

    companion object {
        /** The decision model of the evaluation. */
        const val DEFAULT_MODEL: String = "clef:cloud"

        /** How sure the model has to be, a model that is less sure leaves the mail alone. */
        const val DEFAULT_MINIMUM_CONFIDENCE: Double = 0.5

        /** How many characters of the text go into the state by default. */
        const val DEFAULT_MAX_BODY_LENGTH: Int = 8_000

        /** The question of the pay. */
        const val PAY_MATCHES: String = "pay_matches"

        /** The question of the job description. */
        const val JD_MATCHES: String = "jd_matches"

        /** The question of the days of office. */
        const val OFFICE_DAYS_MATCH: String = "office_days_matches"

        /** The question of the missing pay. */
        const val PAY_MISSING: String = "pay_missing"

        /** The option of a question that holds. */
        const val OPTION_MATCHES: String = "matches"

        /** The option of a question that does not hold. */
        const val OPTION_DOES_NOT_MATCH: String = "does not match"

        private const val MISSING_ANSWER = "The decision model did not answer every question"
    }
}
