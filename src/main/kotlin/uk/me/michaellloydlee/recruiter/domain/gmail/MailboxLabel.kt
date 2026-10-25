package uk.me.michaellloydlee.recruiter.domain.gmail

/**
 * A label of the mailbox.
 *
 * @property id the id the label is addressed with in the API.
 * @property name the full path of the label, for example `Recruiters/Trash`.
 */
data class MailboxLabel(
    val id: String,
    val name: String,
)
