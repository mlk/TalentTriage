package uk.me.michaellloydlee.recruiter.domain.gmail

/**
 * A draft of a reply to a mail of the mailbox.
 *
 * A draft is a mail that is not sent: it waits in the drafts of the job seeker
 * until the job seeker sends it.
 *
 * @property to the recipient of the draft, the sender of the mail it replies to.
 * @property subject the subject of the draft.
 * @property body the text of the draft.
 * @property inReplyTo the id of the mail the draft replies to.
 * @property threadId the id of the thread the draft belongs to.
 */
data class EmailDraft(
    val to: String,
    val subject: String,
    val body: String,
    val inReplyTo: String,
    val threadId: String,
)

/**
 * The subject of a reply to a mail.
 *
 * @param subject the subject of the mail that is replied to.
 * @return the subject of the reply.
 */
fun replySubject(subject: String): String =
    if (subject.trimStart().startsWith(REPLY_PREFIX, ignoreCase = true)) {
        subject
    } else {
        "$REPLY_PREFIX $subject"
    }

private const val REPLY_PREFIX: String = "Re:"
