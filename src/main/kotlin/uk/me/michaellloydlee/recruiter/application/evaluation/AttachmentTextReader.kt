package uk.me.michaellloydlee.recruiter.application.evaluation

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.parser.ExtractionResult
import uk.me.michaellloydlee.recruiter.infrastructure.parser.documentTextExtractor

/**
 * Reads the text of the documents that came with a message.
 *
 * A document whose type is not supported is skipped, a document that cannot be
 * read is skipped as well: the text of the mail itself is still evaluated.
 *
 * @param messages the message endpoints of the Gmail API.
 */
class AttachmentTextReader(
    private val messages: GmailMessagesApi,
) {
    /**
     * Reads the text of the attachments of a message.
     *
     * @param message the message with its attachments.
     * @return the text of the documents, empty when there are none.
     */
    suspend fun textOf(message: EmailMessage): String =
        message.attachments
            .mapNotNull { attachment ->
                val extractor = documentTextExtractor(attachment.mimeType) ?: return@mapNotNull null
                val content = messages.getAttachment(message.id, attachment.id)
                when (val result = extractor.extract(content)) {
                    is ExtractionResult.Extracted -> result.text
                    is ExtractionResult.Failed -> null
                }
            }.filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
}
