package uk.me.michaellloydlee.recruiter.infrastructure.notification

import kotlinx.serialization.Serializable

/**
 * The state of the mail, the state of the evaluation it went through. The
 * names are the wire values the webhook receives.
 */
@Serializable
enum class NotificationState {
    /** The mail is worth a follow up. */
    WORTH_FOLLOW_UP,

    /** The mail needs more information before the job seeker can decide. */
    NEEDS_MORE_INFORMATION,
}

/**
 * A mail of the mailbox that needs the job seeker, the way the webhook
 * receives it.
 *
 * @property id the id of the message.
 * @property sender the value of the `From` header.
 * @property subject the value of the `Subject` header.
 * @property state the state the mail is in.
 */
@Serializable
data class NotificationMail(
    val id: String,
    val sender: String,
    val subject: String,
    val state: NotificationState,
)

/**
 * The body the webhook is called with.
 *
 * @property mails the mails that need the job seeker, an empty body is never
 *   sent.
 */
@Serializable
data class WebhookNotification(
    val mails: List<NotificationMail>,
)
