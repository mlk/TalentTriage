package uk.me.michaellloydlee.recruiter.domain.screening

/**
 * The outcome of the screening of a message.
 *
 * @property decision whether the message is a recruiter mail.
 * @property stage the stage that made the decision.
 * @property reason why the stage decided the way it did, it goes into the log.
 */
data class ScreeningOutcome(
    val decision: ScreeningDecision,
    val stage: ScreeningStage,
    val reason: String,
) {
    /**
     * Whether the message is a recruiter mail.
     */
    val isRecruiter: Boolean
        get() = decision == ScreeningDecision.RECRUITER
}
