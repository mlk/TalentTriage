package uk.me.michaellloydlee.recruiter.application.screening

/**
 * The verdict of the recruiter verification about a message.
 */
sealed interface RecruiterVerification {
    /**
     * The message is from a recruiter.
     */
    data object Recruiter : RecruiterVerification

    /**
     * The message is not from a recruiter.
     */
    data object NotRecruiter : RecruiterVerification

    /**
     * The verification could not decide.
     *
     * @property reason what went wrong, it goes into the log.
     */
    data class Unknown(
        val reason: String,
    ) : RecruiterVerification
}
