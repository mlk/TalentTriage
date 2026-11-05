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
     * The model decided the criteria of the evaluation.
     *
     * A criterion the model was not sure enough about is not known: the answer
     * of the model is only used when the model is sure enough. A criterion
     * that is not known leaves the mail for the job seeker, unless another
     * criterion that is known has decided the state of the mail already.
     *
     * @property jdMatch what the model decided about the profile of the job
     *   seeker.
     * @property officeDays what the job demands of the days of office.
     * @property payMatch what the model decided about the pay of the job.
     */
    data class Evaluated(
        val jdMatch: JobMatch,
        val officeDays: OfficeDays,
        val payMatch: PayMatch,
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
         *
         * A criterion that is not known leaves the mail for the job seeker,
         * but it does not stop a criterion that is known from deciding the
         * state of the mail: a pay that is below the target is a bad fit
         * whatever the model thinks about the rest of the job description.
         */
        override val state: EvaluationState
            get() =
                when {
                    jdMatch == JobMatch.DOES_NOT_MATCH -> EvaluationState.TRASH
                    officeDays == OfficeDays.MORE_THAN_THE_ALLOWED_DAYS -> EvaluationState.TRASH
                    payMatch == PayMatch.MISSING -> EvaluationState.NEEDS_MORE_INFORMATION
                    payMatch == PayMatch.BELOW_THE_TARGET -> EvaluationState.TRASH
                    officeDays == OfficeDays.UNCLEAR -> EvaluationState.NEEDS_MORE_INFORMATION
                    !isKnown -> EvaluationState.NEEDS_MORE_INFORMATION
                    else -> EvaluationState.WORTH_FOLLOW_UP
                }

        /**
         * Whether every criterion of the evaluation is known.
         */
        private val isKnown: Boolean
            get() =
                jdMatch != JobMatch.NOT_KNOWN &&
                    officeDays != OfficeDays.NOT_KNOWN &&
                    payMatch != PayMatch.NOT_KNOWN

        /**
         * What the job seeker still has to find out about the job.
         *
         * The mail can miss the pay and the days of office at the same time,
         * the recruiter is asked about both. A pay the model was not sure
         * about is not a gap: the mail may state a pay, the recruiter cannot
         * be asked about what the model does not know.
         */
        val missingInformation: MissingInformation
            get() =
                MissingInformation(
                    pay = if (payMatch == PayMatch.MISSING) PayInformation.MISSING else PayInformation.KNOWN,
                    officeDays = officeDays == OfficeDays.UNCLEAR,
                )

        /**
         * Why the mail ends in its state, it goes into the log.
         *
         * @return the reason of the state of the mail.
         */
        val reason: String
            get() =
                when {
                    jdMatch == JobMatch.DOES_NOT_MATCH -> DOES_NOT_MATCH_THE_PROFILE
                    officeDays == OfficeDays.MORE_THAN_THE_ALLOWED_DAYS -> TOO_MANY_OFFICE_DAYS
                    payMatch == PayMatch.BELOW_THE_TARGET -> PAY_BELOW_THE_TARGET
                    payMatch == PayMatch.MISSING -> MISSING_PAY
                    officeDays == OfficeDays.UNCLEAR -> MISSING_OFFICE_DAYS
                    jdMatch == JobMatch.NOT_KNOWN -> NOT_SURE_ABOUT_THE_PROFILE
                    payMatch == PayMatch.NOT_KNOWN -> NOT_SURE_ABOUT_THE_PAY
                    officeDays == OfficeDays.NOT_KNOWN -> NOT_SURE_ABOUT_THE_OFFICE_DAYS
                    else -> MATCHES_THE_REQUIREMENTS
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

        /** The message states no pay. */
        const val MISSING_PAY: String = "the message states no pay"

        /** The job description does not match the profile of the job seeker. */
        const val DOES_NOT_MATCH_THE_PROFILE: String =
            "the job description does not match the profile of the job seeker"

        /** The job demands more days of office than the job seeker allows. */
        const val TOO_MANY_OFFICE_DAYS: String =
            "the job demands more days of office than the job seeker allows"

        /** The pay of the job is below the target of the job seeker. */
        const val PAY_BELOW_THE_TARGET: String = "the pay is below the target of the job seeker"

        /** The model was not sure enough about the job description. */
        const val NOT_SURE_ABOUT_THE_PROFILE: String =
            "the model was not sure enough about the job description"

        /** The model was not sure enough about the pay. */
        const val NOT_SURE_ABOUT_THE_PAY: String = "the model was not sure enough about the pay"

        /** The model was not sure enough about the days of office. */
        const val NOT_SURE_ABOUT_THE_OFFICE_DAYS: String =
            "the model was not sure enough about the days of office"

        /** The job matches the requirements of the job seeker. */
        const val MATCHES_THE_REQUIREMENTS: String = "the job matches the requirements of the job seeker"
    }
}
