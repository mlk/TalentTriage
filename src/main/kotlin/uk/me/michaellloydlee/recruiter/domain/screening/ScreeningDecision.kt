package uk.me.michaellloydlee.recruiter.domain.screening

/**
 * The decision of the screening about a message.
 */
enum class ScreeningDecision {
    /** The message is a recruiter mail, it goes on with the deep evaluation. */
    RECRUITER,

    /**
     * The message is a job listing, it offers many jobs at once. It is not
     * evaluated, the job seeker browses it.
     */
    JOB_LISTING,

    /** The message is not a recruiter mail, it is filtered out. */
    NOT_RECRUITER,

    /**
     * The screening could not decide, for example because the model was
     * not reachable. The message stays in the inbox and is looked at again by
     * the next run of the pipeline.
     */
    UNKNOWN,
}
