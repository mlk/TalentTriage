package uk.me.michaellloydlee.recruiter.domain.gmail

/**
 * A document that came with a message.
 *
 * @property id the id the attachment is fetched with.
 * @property fileName the name of the file.
 * @property mimeType the type of the content, for example `application/pdf`.
 * @property size the size of the content in bytes.
 */
data class EmailAttachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
)
