package uk.me.michaellloydlee.recruiter.domain.evaluation

/**
 * What a job description demands of the days of office.
 *
 * A job description rarely states the days of office it wants, so the model
 * has to be able to say that it does not know instead of guessing.
 */
enum class OfficeDays {
    /** The job demands at most the days of office the job seeker allows. */
    AT_MOST_THE_ALLOWED_DAYS,

    /** The job demands more days of office than the job seeker allows. */
    MORE_THAN_THE_ALLOWED_DAYS,

    /** The job does not say how many days of office it demands. */
    UNCLEAR,

    /**
     * The model was not sure enough about the days of office.
     *
     * The answer of the model is only used when the model is sure enough, an
     * answer that is not used leaves its criterion unknown. The answer
     * `unclear` is the exception: it is never a reason to leave a mail alone.
     */
    NOT_KNOWN,
}
