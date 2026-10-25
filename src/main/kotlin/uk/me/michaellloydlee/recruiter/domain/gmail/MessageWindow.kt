package uk.me.michaellloydlee.recruiter.domain.gmail

import java.time.Duration

/**
 * How far back the pipeline looks for messages.
 *
 * The pipeline is a scheduled job, it processes the mail that arrived since
 * the last run. Older messages are not looked at again.
 *
 * @property duration the length of the window.
 */
data class MessageWindow(
    val duration: Duration,
) {
    companion object {
        /** The default window: the last 24 hours. */
        val LAST_DAY: MessageWindow = MessageWindow(Duration.ofHours(24))
    }
}
