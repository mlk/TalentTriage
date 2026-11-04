package uk.me.michaellloydlee.recruiter.domain.evaluation

/**
 * What the model decided about the pay of the job.
 */
enum class PayMatch {
    /** The mail states a pay that meets the target of the job seeker. */
    MEETS_THE_TARGET,

    /** The mail states a pay that is below the target of the job seeker. */
    BELOW_THE_TARGET,

    /** The mail states no pay at all. */
    MISSING,

    /**
     * The pay does not matter: the requirements of the job seeker name no
     * target and the pay is not asked about.
     */
    DOES_NOT_MATTER,

    /**
     * The model was not sure enough about the pay.
     *
     * The answer of the model is only used when the model is sure enough, an
     * answer that is not used leaves its criterion unknown.
     */
    NOT_KNOWN,
}
