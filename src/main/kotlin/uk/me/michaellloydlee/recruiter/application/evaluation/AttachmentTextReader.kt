package uk.me.michaellloydlee.recruiter.application.evaluation

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import uk.me.michaellloydlee.recruiter.infrastructure.parser.ExtractionResult
import uk.me.michaellloydlee.recruiter.infrastructure.parser.documentTextExtractor

/**
 * Reads the text of the documents that came with a message.
 *
 * A document whose type is not supported is skipped, a document that cannot be
 * read is skipped as well: the text of the mail itself is still evaluated. The
 * failure is logged, the job seeker sees it in the log of the run.
 *
 * @param messages the message endpoints of the Gmail API.
 * @param logger the sink of the log of the run.
 */
class AttachmentTextReader(
    private val messages: GmailMessagesApi,
    private val logger: StructuredLogger,
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
                    is ExtractionResult.Extracted -> {
                        result.text
                    }

                    is ExtractionResult.Failed -> {
                        logger.info(
                            event = EVENT_ATTACHMENT_FAILED,
                            message = "An attachment of the mail could not be read",
                            attributes =
                                mapOf(
                                    "id" to message.id,
                                    "file" to attachment.fileName,
                                    "reason" to result.reason,
                                ),
                        )
                        null
                    }
                }
            }.filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")

    companion object {
        /** The event of the log of a document that could not be read. */
        const val EVENT_ATTACHMENT_FAILED: String = "process-inbox.attachment_failed"
    }
}
