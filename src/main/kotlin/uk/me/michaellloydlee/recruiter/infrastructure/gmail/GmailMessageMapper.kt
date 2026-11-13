package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailAttachment
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import java.util.Base64

/**
 * Maps the messages of the Gmail REST API to the domain model.
 */
internal object GmailMessageMapper {
    /**
     * Maps a message of the API to a message of the domain.
     *
     * @param message the message of the API.
     */
    fun toDomain(message: GmailMessageResource): EmailMessage =
        EmailMessage(
            id = message.id,
            threadId = message.threadId,
            sender = header(message, "From"),
            replyTo = header(message, "Reply-To"),
            messageId = messageId(message),
            subject = header(message, "Subject"),
            body = body(message),
            labelIds = message.labelIds,
            attachments = attachments(message),
        )

    /**
     * The documents that came with the message.
     *
     * The parts of the payload that carry an attachment id are the documents,
     * the parts with a body are the text of the message.
     */
    private fun attachments(message: GmailMessageResource): List<EmailAttachment> {
        val payload = message.payload ?: return emptyList()
        val attachments = mutableListOf<EmailAttachment>()
        collectAttachments(payload, attachments)
        return attachments
    }

    private fun collectAttachments(
        payload: GmailPayloadResource,
        attachments: MutableList<EmailAttachment>,
    ) {
        val attachmentId = payload.body.attachmentId
        if (!attachmentId.isNullOrEmpty()) {
            attachments.add(
                EmailAttachment(
                    id = attachmentId,
                    fileName = payload.fileName,
                    mimeType = payload.mimeType,
                    size = payload.body.size,
                ),
            )
        }
        payload.parts.forEach { collectAttachments(it, attachments) }
    }

    private fun header(
        message: GmailMessageResource,
        name: String,
    ): String =
        message.payload
            ?.headers
            ?.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.value
            .orEmpty()

    /**
     * The message id of the message, without the brackets of the header.
     *
     * The value of the `Message-ID` header is a message id of RFC 5322
     * between brackets: `<18f2c1a4b3d5e6f7@mail.example.com>`. The domain
     * holds the id itself, the brackets are the format of the header.
     *
     * @param message the message of the API.
     * @return the message id of the message, empty when the message has none.
     */
    private fun messageId(message: GmailMessageResource): String =
        header(message, "Message-ID").removeSurrounding("<", ">").trim()

    private fun body(message: GmailMessageResource): String {
        val payload = message.payload ?: return message.snippet
        val parts = textParts(payload)
        if (parts.isEmpty()) {
            return message.snippet
        }
        return parts.joinToString(separator = "\n")
    }

    private fun textParts(payload: GmailPayloadResource): List<String> =
        when {
            payload.parts.isNotEmpty() -> payload.parts.flatMap { textParts(it) }
            !payload.mimeType.startsWith("text/plain") -> emptyList()
            else -> payload.body.data?.let { listOf(decode(it)) } ?: emptyList()
        }

    private fun decode(data: String): String = String(Base64.getUrlDecoder().decode(data), Charsets.UTF_8)
}
