package uk.me.michaellloydlee.recruiter.infrastructure.notification

/**
 * The outcome of a call of the webhook.
 *
 * A call that cannot be made is reported, it never throws: the run decides
 * what it does without the notification, and it goes on either way.
 */
sealed interface WebhookOutcome {
    /** The webhook was called and it answered with a success. */
    data object Sent : WebhookOutcome

    /**
     * The webhook was not notified.
     *
     * @property reason why the call failed.
     */
    data class Failed(
        val reason: String,
    ) : WebhookOutcome
}
