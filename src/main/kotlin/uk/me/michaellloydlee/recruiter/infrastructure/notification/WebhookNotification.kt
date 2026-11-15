package uk.me.michaellloydlee.recruiter.infrastructure.notification

import kotlinx.serialization.Serializable

/**
 * A mail of the mailbox that needs the job seeker, the way the webhook
 * receives it.
 *
 * @property id the id of the message.
 * @property sender the value of the `From` header.
 * @property subject the value of the `Subject` header.
 * @property state the state the mail is in, the name of the state of the
 *   evaluation, `WORTH_FOLLOW_UP` or `NEEDS_MORE_INFORMATION`.
 */
@Serializable
data class NotificationMail(
    val id: String,
    val sender: String,
    val subject: String,
    val state: String,
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
