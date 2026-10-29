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
 * The rules of the job listing decide a listing without the model, the model
 * is the fallback for the messages the rules do not catch: a message the model
 * does not see as a recruiter mail is asked about once more, whether it is a
 * job listing. The second question only refines the state of the message, a
 * model that is not sure about it filters the message out.
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
            val verdict = answer(response.content)
            if (verdict is RecruiterVerification.NotRecruiter) {
                jobListing(message)
            } else {
                verdict
            }
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
     * The fallback of the rules: the model tells whether the message is a job
     * listing.
     *
     * A model that is not sure about it filters the message out: the message
     * is not a recruiter mail, that is what was decided before, and the
     * question only refines that state.
     */
    private suspend fun jobListing(message: EmailMessage): RecruiterVerification =
        try {
            val response =
                client.chat(
                    OllamaChatRequest(
                        model = model,
                        stream = false,
                        options = OllamaOptions(temperature = 0.0),
                        messages =
                            listOf(
                                OllamaMessage(role = OllamaMessage.ROLE_SYSTEM, content = LISTING_PROMPT),
                                OllamaMessage(role = OllamaMessage.ROLE_USER, content = prompt(message)),
                            ),
                    ),
                )
            listingAnswer(response.content)
        } catch (error: OllamaException) {
            RecruiterVerification.NotRecruiter(
                "The model of the recruiter verification answered with an error: ${error.message}, " +
                    "the message is filtered out",
            )
        } catch (error: IOException) {
            RecruiterVerification.NotRecruiter(
                "The model of the recruiter verification could not be reached: ${error.message}, " +
                    "the message is filtered out",
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
                RecruiterVerification.NotRecruiter("The model does not see a recruiter mail in it")
            }

            else -> {
                RecruiterVerification.Unknown(
                    "The model of the recruiter verification answered with '$content'",
                )
            }
        }
    }

    /**
     * Reads the answer of the model to the question about the job listing.
     */
    private fun listingAnswer(content: String): RecruiterVerification {
        val answer = content.trim().lowercase()
        return when {
            answer.isEmpty() -> {
                RecruiterVerification.NotRecruiter(
                    "The model of the recruiter verification did not answer the question about " +
                        "the job listing, the message is filtered out",
                )
            }

            answer.startsWith(ANSWER_YES) -> {
                RecruiterVerification.JobListing
            }

            else -> {
                RecruiterVerification.NotRecruiter(
                    "The model does not see a recruiter mail or a job listing in it",
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
        /**
         * The chat model of the recruiter verification. It is a cloud model: it
         * needs a paid plan of Ollama or usage credits.
         */
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

        private const val LISTING_PROMPT =
            "You screen the inbox of a job seeker. Answer with exactly one word: " +
                "\"yes\" if the message is an alert of a job board that offers many jobs at once, " +
                "and \"no\" otherwise. " +
                "Answer with the word only, without a sentence around it."
    }
}
