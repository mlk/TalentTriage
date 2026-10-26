package uk.me.michaellloydlee.recruiter.application.screening

/**
 * The answer of the local model to the question whether a message is a
 * recruiter mail.
 */
sealed interface LocalClassification {
    /**
     * The model says that the message is a recruiter mail.
     */
    data object Recruiter : LocalClassification

    /**
     * The model says that the message is not a recruiter mail.
     */
    data object NotRecruiter : LocalClassification

    /**
     * The model could not be asked, or it did not answer.
     *
     * @property reason what went wrong, it goes into the log.
     */
    data class Unavailable(
        val reason: String,
    ) : LocalClassification
}
