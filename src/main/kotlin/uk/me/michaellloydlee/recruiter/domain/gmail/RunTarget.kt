package uk.me.michaellloydlee.recruiter.domain.gmail

import java.time.Instant

/**
 * What a run of the pipeline looks at.
 *
 * A run of the inbox looks at the messages that arrived after a moment, a run
 * of a message looks at that message only. The job seeker asks for a run of a
 * message by its id, to see what the rules of the pipeline make of it.
 */
sealed interface RunTarget {
    /**
     * The messages of the inbox that were not processed yet.
     *
     * @property since the moment the run looks back to, the messages that
     *   arrived after it are looked at.
     */
    data class Window(
        val since: Instant,
    ) : RunTarget

    /**
     * One message of the mailbox.
     *
     * @property id the id of the message.
     */
    data class Message(
        val id: String,
    ) : RunTarget
}
