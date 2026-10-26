package uk.me.michaellloydlee.recruiter.application.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatRequest
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaOptions
import java.io.IOException

/**
 * Verifies a message with a chat model of Ollama.
 *
 * The model is asked for one word, the answer is read from it. A model that
 * answers something else is no answer at all: the message is left alone.
 *
 * @param client the client of the chat API.
 * @param model the name of the chat model.
 * @param maxBodyLength how many characters of the body go into the prompt.
 */
class ChatModelVerifier(
    private val client: OllamaChatClient,
    private val model: String = DEFAULT_MODEL,
    private val maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
) : RecruiterVerifier {
    override suspend fun verify(message: EmailMessage): RecruiterVerification =
        try {
            val response =
                client.chat(
                    OllamaChatRequest(
                        model = model,
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
            RecruiterVerification.Unknown(
                "The model of the recruiter verification answered with an error: ${error.message}",
            )
        } catch (error: IOException) {
            RecruiterVerification.Unknown(
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
    private fun answer(content: String): RecruiterVerification {
        val answer = content.trim().lowercase()
        return when {
            answer.isEmpty() -> {
                RecruiterVerification.Unknown("The model of the recruiter verification did not answer")
            }

            answer.startsWith(ANSWER_YES) -> {
                RecruiterVerification.Recruiter
            }

            answer.startsWith(ANSWER_NO) -> {
                RecruiterVerification.NotRecruiter
            }

            else -> {
                RecruiterVerification.Unknown(
                    "The model of the recruiter verification answered with '$content'",
                )
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
        /** The chat model of the recruiter verification. */
        const val DEFAULT_MODEL: String = "minimax-m3.1-flash:cloud"

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
