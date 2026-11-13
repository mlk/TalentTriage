package uk.me.michaellloydlee.recruiter.application.draft

import uk.me.michaellloydlee.recruiter.domain.evaluation.MissingInformation
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailDraft
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.gmail.replyAddress
import uk.me.michaellloydlee.recruiter.domain.gmail.replySubject
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiException
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailDraftsApi
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatRequest
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaException
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaOptions
import java.io.IOException
import java.time.Clock
import java.util.UUID

/**
 * Writes the draft that asks the recruiter for the information the mail does
 * not give.
 *
 * The draft is written by a chat model of Ollama: the model writes the reply in
 * the language of the mail of the recruiter, the pipeline writes it into the
 * mailbox as a draft. The pipeline never sends it, the job seeker reads it and
 * decides.
 *
 * @param client the client of the chat API.
 * @param drafts the draft endpoints of the Gmail API.
 * @param model the name of the chat model.
 * @param maxBodyLength how many characters of the mail go into the prompt.
 * @param clock the clock of the draft, it says when the draft was written.
 */
class DraftWriter(
    private val client: OllamaChatClient,
    private val drafts: GmailDraftsApi,
    private val model: String = DEFAULT_MODEL,
    private val maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Writes the draft for a mail of a recruiter.
     *
     * @param message the mail of the recruiter.
     * @param missing what the mail does not say.
     * @param requirements the requirements of the job seeker.
     * @return what happened to the draft.
     */
    suspend fun write(
        message: EmailMessage,
        missing: MissingInformation,
        requirements: JobRequirements,
    ): DraftOutcome {
        if (!missing.asksAboutPay && !missing.asksAboutOfficeDays) {
            return DraftOutcome.Failed("The mail ${message.id} misses nothing, there is nothing to ask for")
        }
        if (replyAddress(message.sender, message.replyTo).isBlank()) {
            return DraftOutcome.Failed("The mail ${message.id} has no address to reply to")
        }
        val reply = reply(message, missing, requirements)
        return when (reply) {
            is ModelReply.Failed -> DraftOutcome.Failed(reply.reason)
            is ModelReply.Written -> save(message, reply.text)
        }
    }

    /**
     * The reply of the model.
     *
     * @param message the mail of the recruiter.
     * @param missing what the mail does not say.
     * @param requirements the requirements of the job seeker.
     * @return the reply of the model or the reason why there is none.
     */
    private suspend fun reply(
        message: EmailMessage,
        missing: MissingInformation,
        requirements: JobRequirements,
    ): ModelReply =
        try {
            val content = client.chat(request(message, missing, requirements)).content.trim()
            if (content.isEmpty()) {
                ModelReply.Failed("The model of the draft did not write a draft")
            } else {
                ModelReply.Written(content)
            }
        } catch (error: OllamaException) {
            ModelReply.Failed("The model of the draft answered with an error: ${reasonOf(error)}")
        } catch (error: IOException) {
            ModelReply.Failed("The model of the draft could not be reached: ${reasonOf(error)}")
        }

    /**
     * Writes the draft into the mailbox.
     *
     * A network failure leaves the mail without a draft as well: the mail is
     * looked at again by the next run.
     *
     * @param message the mail of the recruiter.
     * @param text the reply of the model.
     * @return what happened to the draft.
     */
    private suspend fun save(
        message: EmailMessage,
        text: String,
    ): DraftOutcome =
        try {
            DraftOutcome.Written(drafts.createDraft(draft(message, text)))
        } catch (error: GmailApiException) {
            notWritten(message, error)
        } catch (error: IOException) {
            notWritten(message, error)
        }

    /**
     * The draft could not be written into the mailbox.
     *
     * @param message the mail of the recruiter.
     * @param error what went wrong.
     * @return the outcome of the draft.
     */
    private fun notWritten(
        message: EmailMessage,
        error: Exception,
    ): DraftOutcome =
        DraftOutcome.Failed("The draft of the mail ${message.id} could not be written: ${reasonOf(error)}")

    /**
     * The reason of a failure.
     *
     * The name of the error is part of the reason: an operator that reads the
     * log has to know what went wrong.
     *
     * @param error the error.
     * @return the reason of the failure.
     */
    private fun reasonOf(error: Exception): String =
        error.message?.let { "${error.javaClass.simpleName}: $it" } ?: error.javaClass.simpleName

    /**
     * The draft of a mail, a reply in the thread of the mail.
     *
     * @param message the mail of the recruiter.
     * @param text the reply of the model.
     * @return the draft.
     */
    private fun draft(
        message: EmailMessage,
        text: String,
    ): EmailDraft =
        EmailDraft(
            to = replyAddress(message.sender, message.replyTo),
            subject = replySubject(message.subject),
            body = text,
            inReplyTo = message.messageId,
            messageId = newMessageId(),
            date = clock.instant(),
            threadId = message.threadId,
        )

    /**
     * The message id of the draft, a message id of RFC 5322.
     *
     * @return the message id of the draft.
     */
    private fun newMessageId(): String = "${UUID.randomUUID()}@$MESSAGE_ID_DOMAIN"

    /**
     * The request with the instruction and the mail of the recruiter.
     *
     * @param message the mail of the recruiter.
     * @param missing what the mail does not say.
     * @param requirements the requirements of the job seeker.
     * @return the request for the model.
     */
    private fun request(
        message: EmailMessage,
        missing: MissingInformation,
        requirements: JobRequirements,
    ): OllamaChatRequest =
        OllamaChatRequest(
            model = model,
            stream = false,
            options = OllamaOptions(temperature = 0.0),
            messages =
                listOf(
                    OllamaMessage(
                        role = OllamaMessage.ROLE_SYSTEM,
                        content = DraftPrompt.instruction(missing, requirements),
                    ),
                    OllamaMessage(
                        role = OllamaMessage.ROLE_USER,
                        content = DraftPrompt.mail(message, maxBodyLength),
                    ),
                ),
        )

    /**
     * The answer of the model that writes the draft.
     */
    private sealed interface ModelReply {
        /**
         * The model wrote a reply.
         *
         * @property text the reply of the model.
         */
        data class Written(
            val text: String,
        ) : ModelReply

        /**
         * The model did not write a reply.
         *
         * @property reason what went wrong, it goes into the log.
         */
        data class Failed(
            val reason: String,
        ) : ModelReply
    }

    companion object {
        /**
         * The chat model that writes the drafts. It is a cloud model: it needs
         * a paid plan of Ollama or usage credits.
         */
        const val DEFAULT_MODEL: String = "minimax-m3.1-flash:cloud"

        /** How many characters of the mail go into the prompt by default. */
        const val DEFAULT_MAX_BODY_LENGTH: Int = 8_000

        /**
         * The domain of the message id of a draft. A message id of RFC 5322
         * is a name and a domain, the domain of a local application is not
         * sent to anybody.
         */
        const val MESSAGE_ID_DOMAIN: String = "talent-triage.local"
    }
}
