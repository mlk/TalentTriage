package uk.me.michaellloydlee.recruiter.application.draft

/**
 * What happened to the draft that asks the recruiter for the information the
 * mail does not give.
 */
sealed interface DraftOutcome {
    /**
     * The draft was written into the mailbox, it waits for the job seeker.
     *
     * @property draftId the id of the draft.
     */
    data class Written(
        val draftId: String,
    ) : DraftOutcome

    /**
     * The draft could not be written, the mail waits for the job seeker.
     *
     * @property reason what went wrong, it goes into the log.
     */
    data class Failed(
        val reason: String,
    ) : DraftOutcome
}
