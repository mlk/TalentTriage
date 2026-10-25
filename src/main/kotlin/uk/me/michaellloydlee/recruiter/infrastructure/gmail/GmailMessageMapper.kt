package uk.me.michaellloydlee.recruiter.infrastructure.gmail

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
            subject = header(message, "Subject"),
            body = body(message),
            labelIds = message.labelIds,
        )

    private fun header(
        message: GmailMessageResource,
        name: String,
    ): String =
        message.payload
            ?.headers
            ?.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.value
            .orEmpty()

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
