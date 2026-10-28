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
     * @property officeDays what the job demands of the days of office.
     * @property payMissing the offer states no pay at all.
     */
    data class Evaluated(
        val payMatches: Boolean,
        val jdMatches: Boolean,
        val officeDays: OfficeDays,
        val payMissing: Boolean,
    ) : JobDescriptionEvaluation {
        /**
         * The state of the pipeline the evaluation leads to.
         *
         * A job that does not match the profile or that demands too much
         * office is not worth a look, a job that matches but says nothing
         * about the pay needs the pay checked. A job that matches but does
         * not say how many days of office it demands needs the work from
         * home check: it is not thrown away, the job seeker looks at it.
         */
        val state: RecruiterLabel
            get() =
                when {
                    !jdMatches -> RecruiterLabel.TRASH
                    officeDays == OfficeDays.MORE_THAN_THE_ALLOWED_DAYS -> RecruiterLabel.TRASH
                    officeDays == OfficeDays.UNCLEAR -> RecruiterLabel.NEEDS_WORK_FROM_HOME_CHECK
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
