package uk.me.michaellloydlee.recruiter.application.evaluation

import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.evaluation.OfficeDays
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
 * Every criterion of the evaluation is one `choice` question, and the model
 * answers all of them in a single request. The question of the days of
 * office has a third option, `unclear`: a job description rarely states the
 * days of office it wants, and the model has to be able to say so instead of
 * guessing. The model also tells how sure it is, a model that is not sure
 * enough leaves the mail alone.
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
        val jdAnswer = answers[JD_MATCHES]
        val officeAnswer = answers[OFFICE_DAYS]
        val notSure = notSureAbout(answers)
        if (notSure != null || jdAnswer == null || officeAnswer == null) {
            return JobDescriptionEvaluation.Unknown(notSure ?: MISSING_ANSWER)
        }
        val officeDays = officeDays(officeAnswer.choice)
        if (officeDays == null) {
            return JobDescriptionEvaluation.Unknown(
                "The decision model answered with an unknown option: ${officeAnswer.choice}",
            )
        }
        val payAnswer = answers[PAY_MATCHES]
        val payMissingAnswer = answers[PAY_MISSING]
        return JobDescriptionEvaluation.Evaluated(
            payMatches = payAnswer == null || payAnswer.choice == OPTION_MATCHES,
            jdMatches = jdAnswer.choice == OPTION_MATCHES,
            officeDays = officeDays,
            payMissing = payMissingAnswer != null && payMissingAnswer.choice == OPTION_MATCHES,
        )
    }

    /**
     * The question the model is not sure enough about.
     *
     * @param answers the answers of the model.
     * @return why the model is not sure enough, `null` when it is sure.
     */
    private fun notSureAbout(answers: Map<String, OllamaDecisionAnswer>): String? =
        answers.entries
            .firstOrNull { (_, answer) ->
                answer.confidence != null && answer.confidence < minimumConfidence
            }?.let { (name, answer) ->
                "The decision model is not sure enough about $name (confidence ${answer.confidence})"
            }

    /**
     * The days of office the model asks for.
     *
     * @param choice the answer of the model.
     * @return the days of office, `null` when the model answered with an
     *   option that is not known.
     */
    private fun officeDays(choice: String?): OfficeDays? =
        when (choice) {
            OPTION_MATCHES -> OfficeDays.AT_MOST_THE_ALLOWED_DAYS
            OPTION_DOES_NOT_MATCH -> OfficeDays.MORE_THAN_THE_ALLOWED_DAYS
            OPTION_UNCLEAR -> OfficeDays.UNCLEAR
            else -> null
        }

    private fun questions(requirements: JobRequirements): Map<String, OllamaDecisionQuestion> {
        val questions =
            mutableMapOf(
                JD_MATCHES to
                    question(
                        instructions =
                            "Does the job description match the profile of the job seeker: " +
                                requirements.profile,
                        options =
                            mapOf(
                                OPTION_MATCHES to "The job description matches the profile of the job seeker",
                                OPTION_DOES_NOT_MATCH to
                                    "The job description does not match the profile of the job seeker",
                            ),
                    ),
                OFFICE_DAYS to
                    question(
                        instructions =
                            "How many days of office per week does the job demand? The job seeker " +
                                "allows at most ${requirements.maximumOfficeDays} days of office per week.",
                        options =
                            mapOf(
                                OPTION_MATCHES to
                                    "The job demands at most ${requirements.maximumOfficeDays} days of " +
                                    "office per week",
                                OPTION_DOES_NOT_MATCH to
                                    "The job demands more than ${requirements.maximumOfficeDays} days of " +
                                    "office per week",
                                OPTION_UNCLEAR to
                                    "The job does not say how many days of office it demands",
                            ),
                    ),
            )
        val pay = requirements.minimumPay
        if (pay != null) {
            questions[PAY_MATCHES] =
                question(
                    instructions =
                        "Does the message state a pay of at least ${pay.description}?",
                    options =
                        mapOf(
                            OPTION_MATCHES to "The message states a pay of at least ${pay.description}",
                            OPTION_DOES_NOT_MATCH to "The message states no pay of at least ${pay.description}",
                        ),
                )
            questions[PAY_MISSING] =
                question(
                    instructions = "Is the pay missing from the message?",
                    options =
                        mapOf(
                            OPTION_MATCHES to "The message states no pay at all",
                            OPTION_DOES_NOT_MATCH to "The message states a pay",
                        ),
                )
        }
        return questions
    }

    private fun question(
        instructions: String,
        options: Map<String, String>,
    ): OllamaDecisionQuestion =
        OllamaDecisionQuestion(
            type = OllamaDecisionQuestion.TYPE_CHOICE,
            instructions = instructions,
            criteria = options,
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
        const val OFFICE_DAYS: String = "office_days"

        /** The question of the missing pay. */
        const val PAY_MISSING: String = "pay_missing"

        /** The option of a question that holds. */
        const val OPTION_MATCHES: String = "matches"

        /** The option of a question that does not hold. */
        const val OPTION_DOES_NOT_MATCH: String = "does not match"

        /** The option of a question the job description does not answer. */
        const val OPTION_UNCLEAR: String = "unclear"

        private const val MISSING_ANSWER = "The decision model did not answer every question"
    }
}
