package uk.me.michaellloydlee.recruiter.application.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatRequest
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaMessage
import java.io.IOException

/**
 * Asks a small local model whether a message is a recruiter mail.
 *
 * The model is a small one (one to three billion parameters) that runs on the
 * local Ollama instance: the answer only has to be a yes or a no, so the
 * latency stays low.
 *
 * A model that cannot be reached does not decide: the answer is
 * [LocalClassification.Unavailable] and the message is left alone, a recruiter
 * mail must not be filtered out because the model was down.
 *
 * @param client the client of the local Ollama instance.
 * @param model the name of the local model.
 * @param maxBodyLength how many characters of the body go into the prompt.
 */
class LocalLlmClassifier(
    private val client: OllamaChatClient,
    private val model: String = DEFAULT_MODEL,
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
                        model = model,
                        messages =
                            listOf(
                                OllamaMessage(role = OllamaMessage.ROLE_SYSTEM, content = SYSTEM_PROMPT),
                                OllamaMessage(role = OllamaMessage.ROLE_USER, content = prompt(message)),
                            ),
                    ),
                )
            answer(response.content)
        } catch (error: OllamaException) {
            LocalClassification.Unavailable("The local model answered with an error: ${error.message}")
        } catch (error: IOException) {
            LocalClassification.Unavailable("The local model could not be reached: ${error.message}")
        }

    /**
     * Reads the answer of the model.
     *
     * The model is asked for one word, but it does not always obey: an answer
     * that starts with "yes" counts as a yes, everything else as a no. An
     * empty answer is no answer at all.
     */
    private fun answer(content: String): LocalClassification {
        val answer = content.trim()
        if (answer.isEmpty()) {
            return LocalClassification.Unavailable("The local model did not answer")
        }
        return if (answer.lowercase().startsWith(ANSWER_YES)) {
            LocalClassification.Recruiter
        } else {
            LocalClassification.NotRecruiter
        }
    }

    private fun prompt(message: EmailMessage): String =
        """
        Subject: ${message.subject}

        Body:
        ${message.body.take(maxBodyLength)}
        """.trimIndent()

    companion object {
        /** The local model of the screening, a small one. */
        const val DEFAULT_MODEL: String = "qwen:1.8b"

        /** How many characters of the body go into the prompt by default. */
        const val DEFAULT_MAX_BODY_LENGTH: Int = 4_000

        /** The answer of the model that means "it is a recruiter mail". */
        const val ANSWER_YES: String = "yes"

        private const val SYSTEM_PROMPT =
            "You screen the inbox of a job seeker. Answer with exactly one word: " +
                "\"yes\" if the message is a recruiter or an employer talking about a job, " +
                "an application, an interview or a job description, and \"no\" otherwise. " +
                "Answer with the word only, without a sentence around it."
    }
}
