package uk.me.michaellloydlee.recruiter.application.evaluation

import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.evaluation.JobMatch
import uk.me.michaellloydlee.recruiter.domain.evaluation.OfficeDays
import uk.me.michaellloydlee.recruiter.domain.evaluation.PayMatch
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
 * enough about a criterion does not decide that criterion.
 *
 * An answer the model is not sure enough about is not used, the criterion it
 * answers stays unknown. A criterion that is unknown leaves the mail for the
 * job seeker, a criterion that is known decides the state of the mail even
 * when the model was not sure about the rest: a pay that is below the target
 * is a bad fit whatever the model thinks about the job description.
 *
 * @param client the client of the decision API.
 * @param model the name of the decision model.
 * @param minimumConfidence how sure the model has to be about a criterion, an
 *   answer the model is less sure about is not used.
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
            evaluation(response.answers, requirements.caresAboutPay)
        } catch (error: OllamaException) {
            JobDescriptionEvaluation.Unknown("The decision model answered with an error: ${error.message}")
        } catch (error: IOException) {
            JobDescriptionEvaluation.Unknown("The decision model could not be reached: ${error.message}")
        }

    private fun evaluation(
        answers: Map<String, OllamaDecisionAnswer>,
        caresAboutPay: Boolean,
    ): JobDescriptionEvaluation {
        val jdAnswer = answers[JD_MATCHES]
        val officeAnswer = answers[OFFICE_DAYS]
        if (jdAnswer == null || officeAnswer == null) {
            return JobDescriptionEvaluation.Unknown(MISSING_ANSWER)
        }
        val jdMatch = jdMatch(jdAnswer)
        val officeDays = officeDays(officeAnswer)
        return when {
            jdMatch == null -> {
                JobDescriptionEvaluation.Unknown(
                    "The decision model answered with an unknown option: ${jdAnswer.choice}",
                )
            }

            officeDays == null -> {
                JobDescriptionEvaluation.Unknown(
                    "The decision model answered with an unknown option: ${officeAnswer.choice}",
                )
            }

            else -> {
                JobDescriptionEvaluation.Evaluated(
                    jdMatch = jdMatch,
                    officeDays = officeDays,
                    payMatch = payMatch(answers[PAY_MATCHES], answers[PAY_MISSING], caresAboutPay),
                )
            }
        }
    }

    /**
     * Whether the answer of the model is sure enough to be used.
     *
     * The gate of the confidence is a rule of the project: an answer is only
     * used when the model is sure enough about it. The answer `unclear` is the
     * exception: it is never a reason to leave a mail alone.
     *
     * @param answer the answer of the model.
     * @return whether the answer may be used.
     */
    private fun isSure(answer: OllamaDecisionAnswer): Boolean {
        if (answer.choice == OPTION_UNCLEAR) {
            return true
        }
        val confidence = answer.confidence ?: return true
        return confidence >= minimumConfidence
    }

    /**
     * What the model decided about the profile of the job seeker.
     *
     * @param answer the answer of the model.
     * @return the match of the profile, `null` when the model answered with
     *   an option that is not known.
     */
    private fun jdMatch(answer: OllamaDecisionAnswer): JobMatch? {
        val match =
            when (answer.choice) {
                OPTION_MATCHES -> JobMatch.MATCHES
                OPTION_DOES_NOT_MATCH -> JobMatch.DOES_NOT_MATCH
                else -> null
            }
        if (match == null || isSure(answer)) {
            return match
        }
        return JobMatch.NOT_KNOWN
    }

    /**
     * The days of office the model asks for.
     *
     * @param answer the answer of the model.
     * @return the days of office, `null` when the model answered with an
     *   option that is not known.
     */
    private fun officeDays(answer: OllamaDecisionAnswer): OfficeDays? {
        val days =
            when (answer.choice) {
                OPTION_MATCHES -> OfficeDays.AT_MOST_THE_ALLOWED_DAYS
                OPTION_DOES_NOT_MATCH -> OfficeDays.MORE_THAN_THE_ALLOWED_DAYS
                OPTION_UNCLEAR -> OfficeDays.UNCLEAR
                else -> null
            }
        if (days == null || isSure(answer)) {
            return days
        }
        return OfficeDays.NOT_KNOWN
    }

    /**
     * What the model decided about the pay of the job.
     *
     * The pay is asked about only when the requirements of the job seeker name
     * a target, the pay does not matter without one. A mail that states no pay
     * at all is a gap: the pay is missing before it is too low. A mail that
     * states a pay that does not meet the target is a bad fit, but only when
     * the model is sure that the mail states a pay.
     *
     * @param payAnswer the answer of the question of the pay.
     * @param payMissingAnswer the answer of the question of the missing pay.
     * @param caresAboutPay whether the requirements name a target.
     * @return what the model decided about the pay.
     */
    private fun payMatch(
        payAnswer: OllamaDecisionAnswer?,
        payMissingAnswer: OllamaDecisionAnswer?,
        caresAboutPay: Boolean,
    ): PayMatch =
        when {
            !caresAboutPay -> PayMatch.DOES_NOT_MATTER
            statesNoPayAtAll(payMissingAnswer) -> PayMatch.MISSING
            payAnswer == null || payMissingAnswer == null -> PayMatch.NOT_KNOWN
            !isSure(payAnswer) -> PayMatch.NOT_KNOWN
            payAnswer.choice == OPTION_MATCHES -> PayMatch.MEETS_THE_TARGET
            isSure(payMissingAnswer) -> PayMatch.BELOW_THE_TARGET
            else -> PayMatch.NOT_KNOWN
        }

    /**
     * Whether the model is sure that the mail states no pay at all.
     *
     * @param answer the answer of the question of the missing pay.
     * @return whether the mail states no pay.
     */
    private fun statesNoPayAtAll(answer: OllamaDecisionAnswer?): Boolean =
        answer != null && isSure(answer) && answer.choice == OPTION_MATCHES

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

        /** How sure the model has to be, a model that is less sure does not decide the criterion. */
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
