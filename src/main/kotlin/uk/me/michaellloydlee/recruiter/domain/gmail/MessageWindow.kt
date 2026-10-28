package uk.me.michaellloydlee.recruiter.domain.gmail

import java.time.Duration
import java.time.format.DateTimeParseException

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

/**
 * Reads the window of a run from an ISO-8601 duration.
 *
 * Both commands of the pipeline read the window the same way, so they look
 * at the same messages.
 *
 * @param duration the length of the window, for example `PT24H` or `P1D`.
 * @return the window of the duration.
 * @throws IllegalArgumentException when the text is not a duration, or when
 *   the duration is zero or negative.
 */
fun messageWindow(duration: String): MessageWindow {
    val length =
        try {
            Duration.parse(duration)
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException(
                "'$duration' is not a duration, use an ISO-8601 duration like PT24H or P1D",
                error,
            )
        }
    require(length > Duration.ZERO) {
        "The window has to be longer than zero, '$duration' is not"
    }
    return MessageWindow(length)
}
