package uk.me.michaellloydlee.recruiter.domain.evaluation

/**
 * What the job seeker still has to find out about the job.
 *
 * A mail that states everything the job seeker needs knows nothing missing, a
 * mail that states no pay and no days of office misses both.
 *
 * @property pay what the mail says about the pay of the job.
 * @property officeDays whether the mail says how many days of office the job
 *   demands.
 */
data class MissingInformation(
    val pay: PayInformation,
    val officeDays: Boolean,
) {
    /**
     * Whether the recruiter has to be asked about the pay.
     */
    val asksAboutPay: Boolean
        get() = pay != PayInformation.KNOWN

    /**
     * Whether the recruiter has to be asked about the days of office.
     */
    val asksAboutOfficeDays: Boolean
        get() = officeDays
}

/**
 * What a mail says about the pay of a job.
 */
enum class PayInformation {
    /** The mail states a pay that meets the target of the job seeker. */
    KNOWN,

    /** The mail states no pay at all. */
    MISSING,

    /** The mail states a pay below the target of the job seeker. */
    BELOW_THE_TARGET,
}
