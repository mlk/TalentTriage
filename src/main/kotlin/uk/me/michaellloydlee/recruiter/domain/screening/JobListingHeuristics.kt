package uk.me.michaellloydlee.recruiter.domain.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage

/**
 * The rules that decide without a model whether a message is a job listing.
 *
 * A job listing is an alert of a job board: it offers many jobs at once, it is
 * not one job. The rules are the first stage of the screening and they are
 * deliberately narrow, because a recruiter mail that is called a listing is
 * lost: a listing is not evaluated.
 *
 * The sender of a board and the sender of a person of the same board are not
 * the same sender: `jobalerts-noreply@linkedin.com` sends the alerts of
 * LinkedIn, `inmail-hit-reply@linkedin.com` sends the mails of the people of
 * LinkedIn. Only the first one is a board.
 *
 * @param senderHints the parts of the sender address of a job board.
 * @param phrases the words a job listing talks about, they are looked for in
 *   the subject and in the body of the message.
 */
class JobListingHeuristics(
    private val senderHints: Collection<String> = DEFAULT_SENDER_HINTS,
    private val phrases: Collection<String> = DEFAULT_PHRASES,
) {
    /**
     * Tells whether the message is a job listing.
     *
     * @param message the message to look at.
     */
    fun isJobListing(message: EmailMessage): Boolean {
        if (senderHints.any { message.sender.contains(it, ignoreCase = true) }) {
            return true
        }
        return phrases.any { phrase ->
            message.subject.contains(phrase, ignoreCase = true) ||
                message.body.contains(phrase, ignoreCase = true)
        }
    }

    companion object {
        /**
         * The parts of the sender address of a job board.
         *
         * The alert of a board is sent by the board itself, a person of the
         * same board is not a board: the address of the alerts is the hint,
         * not the domain of the board.
         */
        val DEFAULT_SENDER_HINTS: Set<String> =
            setOf(
                "job alert",
                "jobalert",
                "indeed",
                "otta.com",
                "wellfound",
                "jooble",
                "glassdoor",
                "ziprecruiter",
                "monster.com",
                "totaljobs",
                "reed.co.uk",
                "dice.com",
                "builtin.com",
            )

        /**
         * The words a job listing talks about.
         *
         * They are the phrases of an alert, they do not say anything about one
         * job: a recruiter mail talks about one position and is not caught.
         */
        val DEFAULT_PHRASES: Set<String> =
            setOf(
                "job alert",
                "jobs for you",
                "new jobs for you",
                "jobs match your",
                "jobs matching your",
                "recommended jobs",
                "job recommendations",
                "jobs you might like",
                "jobs you may like",
                "jobs picked for you",
                "jobs you could be interested in",
                "jobs you may be interested in",
                "jobs similar to",
                "trending jobs",
                "browse jobs",
                "see more jobs",
                "new openings for you",
                "roles for you",
            )
    }
}
