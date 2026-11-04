package uk.me.michaellloydlee.recruiter.domain.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals

class JobDescriptionEvaluationTest {
    @Test
    fun aJobThatMatchesIsWorthAFollowUp() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = MEETS_THE_TARGET)

        assertEquals(EvaluationState.WORTH_FOLLOW_UP, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashed() {
        val evaluation = evaluation(jdMatch = DOES_NOT_MATCH, officeDays = AT_MOST, payMatch = MEETS_THE_TARGET)

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobThatDemandsTooMuchOfficeIsTrashed() {
        val evaluation =
            evaluation(
                jdMatch = MATCHES,
                officeDays = OfficeDays.MORE_THAN_THE_ALLOWED_DAYS,
                payMatch = MEETS_THE_TARGET,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithoutAPayNeedsMoreInformation() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = MISSING)

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobWithATooLowPayIsTrashed() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = BELOW_THE_TARGET)

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithATooLowPayIsTrashedEvenWithUnclearOfficeDays() {
        // The pay wins over the days of office that are not stated: the job
        // seeker does not want the job whatever the days of office are, so
        // the recruiter is not asked about them.
        val evaluation =
            evaluation(
                jdMatch = MATCHES,
                officeDays = OfficeDays.UNCLEAR,
                payMatch = BELOW_THE_TARGET,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashedEvenWithoutAPay() {
        val evaluation = evaluation(jdMatch = DOES_NOT_MATCH, officeDays = AT_MOST, payMatch = MISSING)

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithUnclearOfficeDaysNeedsMoreInformation() {
        val evaluation =
            evaluation(
                jdMatch = MATCHES,
                officeDays = OfficeDays.UNCLEAR,
                payMatch = MEETS_THE_TARGET,
            )

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobWithUnclearOfficeDaysNeedsMoreInformationEvenWithoutAPay() {
        val evaluation =
            evaluation(
                jdMatch = MATCHES,
                officeDays = OfficeDays.UNCLEAR,
                payMatch = MISSING,
            )

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashedEvenWithUnclearOfficeDays() {
        val evaluation =
            evaluation(
                jdMatch = DOES_NOT_MATCH,
                officeDays = OfficeDays.UNCLEAR,
                payMatch = MEETS_THE_TARGET,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobTheModelIsNotSureAboutNeedsMoreInformation() {
        val evaluation = evaluation(jdMatch = NOT_KNOWN, officeDays = AT_MOST, payMatch = MEETS_THE_TARGET)

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobTheModelIsNotSureAboutThePayOfNeedsMoreInformation() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = PayMatch.NOT_KNOWN)

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobTheModelIsNotSureAboutTheOfficeDaysOfNeedsMoreInformation() {
        val evaluation =
            evaluation(
                jdMatch = MATCHES,
                officeDays = OfficeDays.NOT_KNOWN,
                payMatch = MEETS_THE_TARGET,
            )

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobWithATooLowPayIsTrashedEvenWhenTheModelIsNotSureAboutTheProfile() {
        // The pay decides the mail on its own: the job seeker does not want
        // the job whatever the job description says, the recruiter cannot
        // change the pay.
        val evaluation =
            evaluation(
                jdMatch = NOT_KNOWN,
                officeDays = AT_MOST,
                payMatch = BELOW_THE_TARGET,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashedEvenWhenTheModelIsNotSureAboutThePay() {
        val evaluation =
            evaluation(
                jdMatch = DOES_NOT_MATCH,
                officeDays = AT_MOST,
                payMatch = PayMatch.NOT_KNOWN,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobThatDemandsTooMuchOfficeIsTrashedEvenWhenTheModelIsNotSureAboutTheProfile() {
        val evaluation =
            evaluation(
                jdMatch = NOT_KNOWN,
                officeDays = OfficeDays.MORE_THAN_THE_ALLOWED_DAYS,
                payMatch = MEETS_THE_TARGET,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithoutAPayNeedsMoreInformationEvenWhenTheModelIsNotSureAboutTheProfile() {
        // The pay is missing before it is too low: the mail waits for the job
        // seeker, the recruiter is asked about the pay.
        val evaluation =
            evaluation(
                jdMatch = NOT_KNOWN,
                officeDays = AT_MOST,
                payMatch = MISSING,
            )

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aPayThatDoesNotMatterDoesNotStopTheMail() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = PayMatch.DOES_NOT_MATTER)

        assertEquals(EvaluationState.WORTH_FOLLOW_UP, evaluation.state)
    }

    @Test
    fun anEvaluationTheModelCouldNotMakeNeedsMoreInformation() {
        val evaluation = JobDescriptionEvaluation.Unknown("The decision model could not be reached")

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobThatIsWorthAFollowUpMissesNothing() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = MEETS_THE_TARGET)

        assertEquals(PayInformation.KNOWN, evaluation.missingInformation.pay)
        assertEquals(false, evaluation.missingInformation.officeDays)
        assertEquals(false, evaluation.missingInformation.asksAboutPay)
        assertEquals(false, evaluation.missingInformation.asksAboutOfficeDays)
    }

    @Test
    fun aJobWithoutAPayMissesThePay() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = MISSING)

        assertEquals(PayInformation.MISSING, evaluation.missingInformation.pay)
        assertEquals(true, evaluation.missingInformation.asksAboutPay)
        assertEquals(false, evaluation.missingInformation.asksAboutOfficeDays)
    }

    @Test
    fun aJobWithATooLowPayIsNotAskedAboutThePay() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = BELOW_THE_TARGET)

        assertEquals(PayInformation.KNOWN, evaluation.missingInformation.pay)
        assertEquals(false, evaluation.missingInformation.asksAboutPay)
    }

    @Test
    fun aJobWithUnclearOfficeDaysMissesTheOfficeDays() {
        val evaluation =
            evaluation(
                jdMatch = MATCHES,
                officeDays = OfficeDays.UNCLEAR,
                payMatch = MEETS_THE_TARGET,
            )

        assertEquals(PayInformation.KNOWN, evaluation.missingInformation.pay)
        assertEquals(true, evaluation.missingInformation.officeDays)
        assertEquals(true, evaluation.missingInformation.asksAboutOfficeDays)
    }

    @Test
    fun aJobThatMissesThePayAndTheOfficeDaysMissesBoth() {
        val evaluation =
            evaluation(
                jdMatch = MATCHES,
                officeDays = OfficeDays.UNCLEAR,
                payMatch = MISSING,
            )

        assertEquals(PayInformation.MISSING, evaluation.missingInformation.pay)
        assertEquals(true, evaluation.missingInformation.officeDays)
        assertEquals(true, evaluation.missingInformation.asksAboutPay)
        assertEquals(true, evaluation.missingInformation.asksAboutOfficeDays)
    }

    @Test
    fun aPayTheModelIsNotSureAboutIsNotAGap() {
        // The mail may state a pay, the recruiter cannot be asked about what
        // the model does not know.
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = PayMatch.NOT_KNOWN)

        assertEquals(PayInformation.KNOWN, evaluation.missingInformation.pay)
        assertEquals(false, evaluation.missingInformation.asksAboutPay)
    }

    @Test
    fun theReasonOfAJobThatIsWorthAFollowUp() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = MEETS_THE_TARGET)

        assertEquals("the job matches the requirements of the job seeker", evaluation.reason)
    }

    @Test
    fun theReasonOfAJobWithATooLowPay() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = BELOW_THE_TARGET)

        assertEquals("the pay is below the target of the job seeker", evaluation.reason)
    }

    @Test
    fun theReasonOfAJobWithoutAPay() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = MISSING)

        assertEquals("the message states no pay", evaluation.reason)
    }

    @Test
    fun theReasonOfAJobWithUnclearOfficeDays() {
        val evaluation =
            evaluation(
                jdMatch = MATCHES,
                officeDays = OfficeDays.UNCLEAR,
                payMatch = MEETS_THE_TARGET,
            )

        assertEquals(
            "the job description does not say how many days of office it demands",
            evaluation.reason,
        )
    }

    @Test
    fun theReasonOfAJobTheModelIsNotSureAbout() {
        val evaluation = evaluation(jdMatch = NOT_KNOWN, officeDays = AT_MOST, payMatch = MEETS_THE_TARGET)

        assertEquals("the model was not sure enough about the job description", evaluation.reason)
    }

    @Test
    fun theReasonOfAJobTheModelIsNotSureAboutThePayOf() {
        val evaluation = evaluation(jdMatch = MATCHES, officeDays = AT_MOST, payMatch = PayMatch.NOT_KNOWN)

        assertEquals("the model was not sure enough about the pay", evaluation.reason)
    }

    private fun evaluation(
        jdMatch: JobMatch,
        officeDays: OfficeDays,
        payMatch: PayMatch,
    ): JobDescriptionEvaluation.Evaluated =
        JobDescriptionEvaluation.Evaluated(
            jdMatch = jdMatch,
            officeDays = officeDays,
            payMatch = payMatch,
        )

    companion object {
        private val AT_MOST: OfficeDays = OfficeDays.AT_MOST_THE_ALLOWED_DAYS
        private val MATCHES: JobMatch = JobMatch.MATCHES
        private val DOES_NOT_MATCH: JobMatch = JobMatch.DOES_NOT_MATCH
        private val NOT_KNOWN: JobMatch = JobMatch.NOT_KNOWN
        private val MEETS_THE_TARGET: PayMatch = PayMatch.MEETS_THE_TARGET
        private val BELOW_THE_TARGET: PayMatch = PayMatch.BELOW_THE_TARGET
        private val MISSING: PayMatch = PayMatch.MISSING
    }
}
