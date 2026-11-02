package uk.me.michaellloydlee.recruiter.domain.evaluation

/**
 * The evaluation of a job description against the requirements of the job
 * seeker.
 */
sealed interface JobDescriptionEvaluation {
    /**
     * The state of the pipeline the evaluation leads to.
     */
    val state: EvaluationState

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
         * office is not worth a look. A job whose pay is below the target is
         * not worth a look either: the recruiter cannot change it, the job
         * seeker would not take the job. A job that matches but does not say
         * everything the job seeker needs to know - the pay, the days of
         * office - is not thrown away either: it waits for the job seeker.
         *
         * A mail that states no pay at all does not meet the target either,
         * the pay is missing before it is too low: such a mail is a gap and
         * not a bad fit.
         */
        override val state: EvaluationState
            get() =
                when {
                    !jdMatches -> EvaluationState.TRASH
                    officeDays == OfficeDays.MORE_THAN_THE_ALLOWED_DAYS -> EvaluationState.TRASH
                    payMissing -> EvaluationState.NEEDS_MORE_INFORMATION
                    !payMatches -> EvaluationState.TRASH
                    officeDays == OfficeDays.UNCLEAR -> EvaluationState.NEEDS_MORE_INFORMATION
                    else -> EvaluationState.WORTH_FOLLOW_UP
                }

        /**
         * What the job seeker still has to find out about the job.
         *
         * The mail can miss the pay and the days of office at the same time,
         * the recruiter is asked about both.
         */
        val missingInformation: MissingInformation
            get() =
                MissingInformation(
                    pay = if (payMissing) PayInformation.MISSING else PayInformation.KNOWN,
                    officeDays = officeDays == OfficeDays.UNCLEAR,
                )

        /**
         * What the job seeker still has to find out about the job.
         *
         * @return what is missing, `null` when nothing is missing.
         */
        val missing: String?
            get() =
                when {
                    missingInformation.officeDays -> MISSING_OFFICE_DAYS
                    missingInformation.pay == PayInformation.MISSING -> MISSING_PAY
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
        override val state: EvaluationState = EvaluationState.NEEDS_MORE_INFORMATION
    }

    companion object {
        /** The job description does not say how many days of office it demands. */
        const val MISSING_OFFICE_DAYS: String =
            "the job description does not say how many days of office it demands"

        /** The message states no pay at all. */
        const val MISSING_PAY: String = "the message states no pay"
    }
}
