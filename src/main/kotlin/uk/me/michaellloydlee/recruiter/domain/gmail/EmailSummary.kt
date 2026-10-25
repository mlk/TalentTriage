package uk.me.michaellloydlee.recruiter.domain.gmail

/**
 * A message of the mailbox without its content.
 *
 * @property id the id of the message.
 * @property threadId the id of the thread the message belongs to.
 */
data class EmailSummary(
    val id: String,
    val threadId: String,
)
