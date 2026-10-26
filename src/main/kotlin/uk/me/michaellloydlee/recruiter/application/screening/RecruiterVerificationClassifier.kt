package uk.me.michaellloydlee.recruiter.application.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatRequest
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaOptions
import java.io.IOException

/**
 * Asks the recruiter verification model whether a message is a recruiter mail.
 *
 * The model runs on the local Ollama instance, which may forward the request
 * to a cloud model: the answer only has to be a yes or a no, so the latency
 * stays low. Every step of the pipeline will have its own model - the
 * recruiter verification here, the verification of the job descriptions and
 the answers to the recruiters later.
 *
 * A model that cannot be reached does not decide: the answer is
 * [LocalClassification.Unavailable] and the message is left alone, a recruiter
 * mail must not be filtered out because the model was down.
 *
 * @param client the client of the local Ollama instance.
 * @param recruiterVerificationModel the name of the model of the recruiter
 *   verification.
 * @param maxBodyLength how many characters of the body go into the prompt.
 */
class RecruiterVerificationClassifier(
    private val client: OllamaChatClient,
    private val recruiterVerificationModel: String = DEFAULT_RECRUITER_VERIFICATION_MODEL,
    private val maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
) {
    /**
     * Asks the model about a message.
     *
     * @param message the message to look at.
     * @return what the model answered.
     */
    suspend fun classify(message: EmailMessage): LocalClassification =
        try {
            val response =
                client.chat(
                    OllamaChatRequest(
                        model = recruiterVerificationModel,
                        stream = false,
                        options = OllamaOptions(temperature = 0.0),
                        messages =
                            listOf(
                                OllamaMessage(role = OllamaMessage.ROLE_SYSTEM, content = SYSTEM_PROMPT),
                                OllamaMessage(role = OllamaMessage.ROLE_USER, content = prompt(message)),
                            ),
                    ),
                )
            answer(response.content)
        } catch (error: OllamaException) {
            LocalClassification.Unavailable(
                "The model of the recruiter verification answered with an error: ${error.message}",
            )
        } catch (error: IOException) {
            LocalClassification.Unavailable(
                "The model of the recruiter verification could not be reached: ${error.message}",
            )
        }

    /**
     * Reads the answer of the model.
     *
     * The model is asked for one word, but it does not always obey: an answer
     * that starts with "yes" counts as a yes and one that starts with "no" as
     * a no. Everything else is no answer at all - a small model sometimes
     * answers with something else entirely, and that must not be read as a
     * "no": the message stays in the inbox and is looked at again.
     */
    private fun answer(content: String): LocalClassification {
        val answer = content.trim().lowercase()
        return when {
            answer.isEmpty() -> {
                LocalClassification.Unavailable(
                    "The model of the recruiter verification did not answer",
                )
            }

            answer.startsWith(ANSWER_YES) -> {
                LocalClassification.Recruiter
            }

            answer.startsWith(ANSWER_NO) -> {
                LocalClassification.NotRecruiter
            }

            else -> {
                LocalClassification.Unavailable("The model of the recruiter verification answered with '$content'")
            }
        }
    }

    private fun prompt(message: EmailMessage): String =
        """
        Subject: ${message.subject}

        Body:
        ${message.body.take(maxBodyLength)}
        """.trimIndent()

    companion object {
        /** The model of the recruiter verification. */
        const val DEFAULT_RECRUITER_VERIFICATION_MODEL: String = "minimax-m3.1-flash:cloud"

        /** How many characters of the body go into the prompt by default. */
        const val DEFAULT_MAX_BODY_LENGTH: Int = 4_000

        /** The answer of the model that means "it is a recruiter mail". */
        const val ANSWER_YES: String = "yes"

        /** The answer of the model that means "it is not a recruiter mail". */
        const val ANSWER_NO: String = "no"

        private const val SYSTEM_PROMPT =
            "You screen the inbox of a job seeker. Answer with exactly one word: " +
                "\"yes\" if the message is a recruiter or an employer talking about a job, " +
                "an application, an interview or a job description, and \"no\" otherwise. " +
                "Answer with the word only, without a sentence around it."
    }
}
