package uk.me.michaellloydlee.recruiter.domain.evaluation

import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel

/**
 * The evaluation of a job description against the requirements of the job
 * seeker.
 */
sealed interface JobDescriptionEvaluation {
    /**
     * The state of the pipeline the evaluation leads to.
     */
    val state: RecruiterLabel

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
         * office is not worth a look. A job that matches but does not say
         * everything the job seeker needs to know - the pay, the days of
         * office - is not thrown away either: it waits for the job seeker.
         */
        override val state: RecruiterLabel
            get() =
                when {
                    !jdMatches -> RecruiterLabel.TRASH
                    officeDays == OfficeDays.MORE_THAN_THE_ALLOWED_DAYS -> RecruiterLabel.TRASH
                    officeDays == OfficeDays.UNCLEAR -> RecruiterLabel.NEEDS_MORE_INFORMATION
                    payMissing || !payMatches -> RecruiterLabel.NEEDS_MORE_INFORMATION
                    else -> RecruiterLabel.WORTH_FOLLOW_UP
                }

        /**
         * What the job seeker still has to find out about the job.
         *
         * @return what is missing, `null` when nothing is missing.
         */
        val missing: String?
            get() =
                when {
                    officeDays == OfficeDays.UNCLEAR -> MISSING_OFFICE_DAYS
                    payMissing -> MISSING_PAY
                    !payMatches -> PAY_BELOW_THE_TARGET
                    else -> null
                }
    }

    /**
     * The model could not decide, the mail waits for the job seeker.
     *
     * The state of an unknown evaluation is
     * `Recruiters/NeedsMoreInformation`: the job seeker looks at the mail, the
     * reason of the model goes into the log.
     *
     * @property reason what went wrong, it goes into the log.
     */
    data class Unknown(
        val reason: String,
    ) : JobDescriptionEvaluation {
        override val state: RecruiterLabel = RecruiterLabel.NEEDS_MORE_INFORMATION
    }

    companion object {
        /** The job description does not say how many days of office it demands. */
        const val MISSING_OFFICE_DAYS: String =
            "the job description does not say how many days of office it demands"

        /** The message states no pay at all. */
        const val MISSING_PAY: String = "the message states no pay"

        /** The pay of the message is below the target of the job seeker. */
        const val PAY_BELOW_THE_TARGET: String = "the pay is below the target"
    }
}
