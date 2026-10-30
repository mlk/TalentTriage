package uk.me.michaellloydlee.recruiter.domain.gmail

import java.time.Duration
import java.time.Instant
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
 * The window of a run of the pipeline.
 *
 * The window is the gap since the last successful run: the run looks back to
 * the moment of that run and no further. The mail that was screened out is
 * looked at once and the mail of an outage is not lost.
 *
 * The command line wins over the state of the last run: a window that was
 * given on the command line is the window of the run.
 *
 * @param lastRun the moment of the last successful run, `null` when no run
 *   succeeded yet.
 * @param now the moment of the run.
 * @param given the window of the command line, `null` when it was not given.
 * @return the window of the run.
 */
fun runWindow(
    lastRun: Instant?,
    now: Instant,
    given: MessageWindow?,
): MessageWindow =
    when {
        given != null -> given
        lastRun != null && lastRun.isBefore(now) -> MessageWindow(Duration.between(lastRun, now))
        else -> MessageWindow.LAST_DAY
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
