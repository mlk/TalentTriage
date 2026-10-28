package uk.me.michaellloydlee.recruiter.domain.evaluation

import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel

/**
 * The evaluation of a job description against the requirements of the job
 * seeker.
 */
sealed interface JobDescriptionEvaluation {
    /**
     * The four criteria of the evaluation.
     *
     * @property payMatches the offer states a pay that meets the target.
     * @property jdMatches the job description matches the profile.
     * @property officeDaysMatch the job does not demand more office days than
     *   the job seeker wants.
     * @property payMissing the offer states no pay at all.
     */
    data class Evaluated(
        val payMatches: Boolean,
        val jdMatches: Boolean,
        val officeDaysMatch: Boolean,
        val payMissing: Boolean,
    ) : JobDescriptionEvaluation {
        /**
         * The state of the pipeline the evaluation leads to.
         *
         * A job that does not match the profile or that demands too much
         * office is not worth a look, a job that matches but says nothing
         * about the pay needs the pay checked.
         */
        val state: RecruiterLabel
            get() =
                when {
                    !jdMatches || !officeDaysMatch -> RecruiterLabel.TRASH
                    payMissing || !payMatches -> RecruiterLabel.NEEDS_PAY_CHECK
                    else -> RecruiterLabel.WORTH_FOLLOW_UP
                }
    }

    /**
     * The model could not decide, the mail stays in the inbox.
     *
     * @property reason what went wrong, it goes into the log.
     */
    data class Unknown(
        val reason: String,
    ) : JobDescriptionEvaluation
}
