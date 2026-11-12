package uk.me.michaellloydlee.recruiter.domain.gmail

/**
 * A message of the mailbox, independent of the API it came from.
 *
 * @property id the id of the message.
 * @property threadId the id of the thread the message belongs to.
 * @property sender the value of the `From` header.
 * @property replyTo the value of the `Reply-To` header, empty when the mail
 *   has none: the answer goes to that address and not to the address of the
 *   `From` header.
 * @property messageId the value of the `Message-ID` header, a message id of
 *   RFC 5322. It is empty when the mail has none.
 * @property subject the value of the `Subject` header.
 * @property body the plain text body of the message.
 * @property labelIds the ids of the labels the message carries.
 * @property attachments the documents that came with the message.
 */
data class EmailMessage(
    val id: String,
    val threadId: String,
    val sender: String,
    val subject: String,
    val body: String,
    val labelIds: List<String>,
    val attachments: List<EmailAttachment> = emptyList(),
    val replyTo: String = "",
    val messageId: String = "",
)
