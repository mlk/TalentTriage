package uk.me.michaellloydlee.recruiter.domain.gmail

/**
 * A message of the mailbox, independent of the API it came from.
 *
 * @property id the id of the message.
 * @property threadId the id of the thread the message belongs to.
 * @property sender the value of the `From` header.
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
)
