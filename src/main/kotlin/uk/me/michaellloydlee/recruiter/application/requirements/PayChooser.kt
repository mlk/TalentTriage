package uk.me.michaellloydlee.recruiter.application.requirements

import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import uk.me.michaellloydlee.recruiter.domain.requirements.PayScale
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionAnswer
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionQuestion
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionRequest
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import java.io.IOException

/**
 * Lets the decision model of Ollama name the minimum pay of the job seeker.
 *
 * The decision API answers questions, it does not write: the model chooses
 * the pay of the scale of the market the job seeker wants at least. The bands
 * of the scale carry the level of the role they are the pay of, the model
 * knows the level of the job seeker and not the pays of the market.
 *
 * The confidence of the answer is not a gate here: the model ranks the pays
 * of the market and the concentration of a ranking over the neighbouring
 * levels is low by nature - a model that is sure of the level of the job
 * seeker reports a confidence of 0.45. The answer is a suggestion the job
 * seeker reads and changes, it is not a state of the pipeline.
 *
 * @param client the client of the decision API.
 * @param model the name of the decision model.
 * @param maxStateLength how many characters of the CV go into the state.
 */
class PayChooser(
    private val client: OllamaDecisionClient,
    private val model: String = DEFAULT_MODEL,
    private val maxStateLength: Int = DEFAULT_MAX_STATE_LENGTH,
) {
    /**
     * The pay the model named.
     *
     * @param cv the text of the CV.
     * @param scale the pays of the market the model chooses from.
     * @return the pay of the job seeker, the reason why there is none.
     */
    suspend fun choose(
        cv: String,
        scale: PayScale,
    ): PayOutcome =
        try {
            val response =
                client.decide(
                    OllamaDecisionRequest(
                        model = model,
                        state = cv.take(maxStateLength),
                        questions = mapOf(QUESTION_MINIMUM_PAY to question(scale)),
                    ),
                )
            answer(response.answers[QUESTION_MINIMUM_PAY], scale)
        } catch (error: OllamaException) {
            PayOutcome.Failed("The decision model answered with an error: ${error.message}")
        } catch (error: IOException) {
            PayOutcome.Failed("The decision model could not be reached: ${error.message}")
        }

    /**
     * The question of the pay.
     *
     * @param scale the pays of the market.
     * @return the question with the pays of the scale as its options.
     */
    private fun question(scale: PayScale): OllamaDecisionQuestion =
        OllamaDecisionQuestion(
            type = OllamaDecisionQuestion.TYPE_CHOICE,
            instructions = INSTRUCTIONS,
            criteria =
                scale.bands
                    .mapIndexed { index, band -> index.toString() to band.option }
                    .toMap(),
        )

    /**
     * The pay the model named.
     *
     * @param answer the answer of the model, `null` when the model did not
     *   answer the question.
     * @param scale the pays of the market.
     * @return the pay of the job seeker, the reason why there is none.
     */
    private fun answer(
        answer: OllamaDecisionAnswer?,
        scale: PayScale,
    ): PayOutcome {
        if (answer == null) {
            return PayOutcome.Unclear("The decision model did not answer the question of the pay")
        }
        val choice = answer.choice
        val band = scale.bandOf(choice.orEmpty())
        return if (band == null) {
            PayOutcome.Unclear("The decision model answered with an unknown option: $choice")
        } else {
            PayOutcome.Chosen(band.pay)
        }
    }

    companion object {
        /** The decision model that names the pay. */
        const val DEFAULT_MODEL: String = "clef:cloud"

        /** How many characters of the CV go into the state by default. */
        const val DEFAULT_MAX_STATE_LENGTH: Int = 8_000

        /** The question of the pay. */
        const val QUESTION_MINIMUM_PAY: String = "minimum_pay"

        private const val INSTRUCTIONS =
            "What is the minimum annual pay the job seeker is looking for? The options are the " +
                "pays of the market for the levels of the role, choose the pay of the level of the " +
                "job seeker."
    }
}

/**
 * The outcome of the choice of the pay.
 */
sealed interface PayOutcome {
    /**
     * The model named the pay.
     *
     * @property pay the minimum pay of the job seeker.
     */
    data class Chosen(
        val pay: PayRequirement,
    ) : PayOutcome

    /**
     * The model named no pay.
     *
     * @property reason why the model named no pay.
     */
    data class Unclear(
        val reason: String,
    ) : PayOutcome

    /**
     * The model could not be asked.
     *
     * @property reason why the model could not be asked.
     */
    data class Failed(
        val reason: String,
    ) : PayOutcome
}
