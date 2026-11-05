package uk.me.michaellloydlee.recruiter.domain.evaluation

/**
 * What the model decided about the job description matching the profile of the
 * job seeker.
 */
enum class JobMatch {
    /** The job description matches the profile of the job seeker. */
    MATCHES,

    /** The job description does not match the profile of the job seeker. */
    DOES_NOT_MATCH,

    /**
     * The model was not sure enough about the job description.
     *
     * The answer of the model is only used when the model is sure enough, an
     * answer that is not used leaves its criterion unknown.
     */
    NOT_KNOWN,
}
