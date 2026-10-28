package uk.me.michaellloydlee.recruiter.application.evaluation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.evaluation.OfficeDays
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatRequest
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaOptions
import java.io.IOException

/**
 * Evaluates a job description with a chat model of Ollama.
 *
 * The model is asked for a JSON object with the four criteria of the
 * evaluation, the answer is read from it. A model that answers something else
 * leaves the mail alone.
 *
 * @param client the client of the chat API.
 * @param model the name of the chat model.
 * @param maxBodyLength how many characters of the text go into the prompt.
 */
class ChatModelEvaluator(
    private val client: OllamaChatClient,
    private val model: String = DEFAULT_MODEL,
    private val maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
) : JobDescriptionEvaluator {
    override suspend fun evaluate(
        message: EmailMessage,
        attachments: String,
        requirements: JobRequirements,
    ): JobDescriptionEvaluation =
        try {
            val response =
                client.chat(
                    OllamaChatRequest(
                        model = model,
                        stream = false,
                        options = OllamaOptions(temperature = 0.0),
                        messages =
                            listOf(
                                OllamaMessage(
                                    role = OllamaMessage.ROLE_SYSTEM,
                                    content = systemPrompt(requirements),
                                ),
                                OllamaMessage(
                                    role = OllamaMessage.ROLE_USER,
                                    content = evaluationText(message, attachments).take(maxBodyLength),
                                ),
                            ),
                    ),
                )
            answer(response.content)
        } catch (error: OllamaException) {
            JobDescriptionEvaluation.Unknown("The model of the evaluation answered with an error: ${error.message}")
        } catch (error: IOException) {
            JobDescriptionEvaluation.Unknown("The model of the evaluation could not be reached: ${error.message}")
        }

    private fun answer(content: String): JobDescriptionEvaluation {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return JobDescriptionEvaluation.Unknown("The model of the evaluation did not answer with a JSON object")
        }
        val answer =
            try {
                JSON.decodeFromString(EvaluationAnswer.serializer(), content.substring(start, end + 1))
            } catch (error: IllegalArgumentException) {
                return JobDescriptionEvaluation.Unknown(
                    "The model of the evaluation answered with an unreadable JSON object: ${error.message}",
                )
            }
        return evaluation(answer)
    }

    private fun evaluation(answer: EvaluationAnswer): JobDescriptionEvaluation {
        val jdMatches = answer.jdMatches
        val officeChoice = answer.officeDays
        if (jdMatches == null || officeChoice == null) {
            return JobDescriptionEvaluation.Unknown(MISSING_ANSWER)
        }
        val officeDays =
            officeDays(officeChoice)
                ?: return JobDescriptionEvaluation.Unknown(
                    "The model of the evaluation answered with an unknown option: $officeChoice",
                )
        return JobDescriptionEvaluation.Evaluated(
            payMatches = answer.payMatches ?: true,
            jdMatches = jdMatches,
            officeDays = officeDays,
            payMissing = answer.payMissing ?: false,
        )
    }

    /**
     * The days of office the model asks for.
     *
     * @param choice the answer of the model.
     * @return the days of office, `null` when the model answered with an
     *   option that is not known.
     */
    private fun officeDays(choice: String): OfficeDays? =
        when (choice) {
            OPTION_MATCHES -> OfficeDays.AT_MOST_THE_ALLOWED_DAYS
            OPTION_DOES_NOT_MATCH -> OfficeDays.MORE_THAN_THE_ALLOWED_DAYS
            OPTION_UNCLEAR -> OfficeDays.UNCLEAR
            else -> null
        }

    private fun systemPrompt(requirements: JobRequirements): String {
        val pay =
            requirements.minimumPay
                ?.let { "at least ${it.description}" }
                ?: "does not matter"
        return """
            You evaluate a job offer for a job seeker. Answer with a JSON object and nothing else,
            with the boolean fields pay_matches, jd_matches and pay_missing and the field
            office_days.
            pay_matches says whether the offer states a pay that meets the target,
            jd_matches whether the job description matches the profile,
            pay_missing whether the offer states no pay at all.
            office_days says what the job demands of the days of office:
            "$OPTION_MATCHES" when the job demands at most
            ${requirements.maximumOfficeDays} days of office per week,
            "$OPTION_DOES_NOT_MATCH" when it demands more,
            "$OPTION_UNCLEAR" when the job does not say how many days
            of office it demands.

            The requirements of the job seeker:
            - the pay is $pay
            - the job may demand at most ${requirements.maximumOfficeDays} days of office per week
            - the profile: ${requirements.profile}

            Answer with the JSON object only, without a sentence around it.
            """.trimIndent()
    }

    @Serializable
    private data class EvaluationAnswer(
        @SerialName("pay_matches")
        val payMatches: Boolean? = null,
        @SerialName("jd_matches")
        val jdMatches: Boolean? = null,
        @SerialName("office_days")
        val officeDays: String? = null,
        @SerialName("pay_missing")
        val payMissing: Boolean? = null,
    )

    companion object {
        /**
         * The chat model of the evaluation. It is a cloud model: it needs a
         * paid plan of Ollama or usage credits.
         */
        const val DEFAULT_MODEL: String = "minimax-m3.1-flash:cloud"

        /** How many characters of the text go into the prompt by default. */
        const val DEFAULT_MAX_BODY_LENGTH: Int = 8_000

        /** The answer of the model: the job demands at most the allowed days. */
        const val OPTION_MATCHES: String = "matches"

        /** The answer of the model: the job demands more than the allowed days. */
        const val OPTION_DOES_NOT_MATCH: String = "does not match"

        /** The answer of the model: the job does not say how many days it demands. */
        const val OPTION_UNCLEAR: String = "unclear"

        private const val MISSING_ANSWER = "The model of the evaluation did not answer every field"

        private val JSON: Json = Json { ignoreUnknownKeys = true }
    }
}
