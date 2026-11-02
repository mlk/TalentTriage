package uk.me.michaellloydlee.recruiter.domain.evaluation

import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel

/**
 * The state an evaluation of a job description leads to.
 *
 * An evaluation leads to one of three states. A job listing is a decision of
 * the screening - an alert of a job board offers many jobs at once, it is not
 * one job and it is not evaluated - so it is not a state of an evaluation.
 *
 * @property label the label of the state, the label the mail carries in the
 *   mailbox.
 */
enum class EvaluationState(
    val label: RecruiterLabel,
) {
    /** The mail is worth a follow up. */
    WORTH_FOLLOW_UP(RecruiterLabel.WORTH_FOLLOW_UP),

    /**
     * The mail matches but does not say everything the job seeker needs to
     * know, it waits for the job seeker.
     */
    NEEDS_MORE_INFORMATION(RecruiterLabel.NEEDS_MORE_INFORMATION),

    /** The mail is not worth any attention. */
    TRASH(RecruiterLabel.TRASH),
}
