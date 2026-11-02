package uk.me.michaellloydlee.recruiter.domain.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals

class JobDescriptionEvaluationTest {
    @Test
    fun aJobThatMatchesIsWorthAFollowUp() {
        val evaluation = evaluation(payMatches = true, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(EvaluationState.WORTH_FOLLOW_UP, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashed() {
        val evaluation = evaluation(payMatches = true, jdMatches = false, officeDays = AT_MOST, payMissing = false)

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobThatDemandsTooMuchOfficeIsTrashed() {
        val evaluation =
            evaluation(
                payMatches = true,
                jdMatches = true,
                officeDays = OfficeDays.MORE_THAN_THE_ALLOWED_DAYS,
                payMissing = false,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithoutAPayNeedsMoreInformation() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = true)

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobWithATooLowPayIsTrashed() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithATooLowPayIsTrashedEvenWithUnclearOfficeDays() {
        // The pay wins over the days of office that are not stated: the job
        // seeker does not want the job whatever the days of office are, so
        // the recruiter is not asked about them.
        val evaluation =
            evaluation(
                payMatches = false,
                jdMatches = true,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = false,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashedEvenWithoutAPay() {
        val evaluation = evaluation(payMatches = false, jdMatches = false, officeDays = AT_MOST, payMissing = true)

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithUnclearOfficeDaysNeedsMoreInformation() {
        val evaluation =
            evaluation(
                payMatches = true,
                jdMatches = true,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = false,
            )

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobWithUnclearOfficeDaysNeedsMoreInformationEvenWithoutAPay() {
        val evaluation =
            evaluation(
                payMatches = false,
                jdMatches = true,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = true,
            )

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashedEvenWithUnclearOfficeDays() {
        val evaluation =
            evaluation(
                payMatches = true,
                jdMatches = false,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = false,
            )

        assertEquals(EvaluationState.TRASH, evaluation.state)
    }

    @Test
    fun aJobThatIsWorthAFollowUpIsMissingNothing() {
        val evaluation = evaluation(payMatches = true, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(null, evaluation.missing)
    }

    @Test
    fun theMissingOfficeDaysAreNamed() {
        val evaluation =
            evaluation(
                payMatches = true,
                jdMatches = true,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = false,
            )

        assertEquals(
            "the job description does not say how many days of office it demands",
            evaluation.missing,
        )
    }

    @Test
    fun theMissingPayIsNamed() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = true)

        assertEquals("the message states no pay", evaluation.missing)
    }

    @Test
    fun aJobWithATooLowPayMissesNothing() {
        // The pay is stated, the job seeker knows what the job pays. The mail
        // is trashed, the recruiter is not asked anything.
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(null, evaluation.missing)
    }

    @Test
    fun anEvaluationTheModelCouldNotMakeNeedsMoreInformation() {
        val evaluation = JobDescriptionEvaluation.Unknown("The decision model could not be reached")

        assertEquals(EvaluationState.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobThatIsWorthAFollowUpMissesNothing() {
        val evaluation = evaluation(payMatches = true, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(PayInformation.KNOWN, evaluation.missingInformation.pay)
        assertEquals(false, evaluation.missingInformation.officeDays)
        assertEquals(false, evaluation.missingInformation.asksAboutPay)
        assertEquals(false, evaluation.missingInformation.asksAboutOfficeDays)
    }

    @Test
    fun aJobWithoutAPayMissesThePay() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = true)

        assertEquals(PayInformation.MISSING, evaluation.missingInformation.pay)
        assertEquals(true, evaluation.missingInformation.asksAboutPay)
        assertEquals(false, evaluation.missingInformation.asksAboutOfficeDays)
    }

    @Test
    fun aJobWithATooLowPayIsNotAskedAboutThePay() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(PayInformation.KNOWN, evaluation.missingInformation.pay)
        assertEquals(false, evaluation.missingInformation.asksAboutPay)
    }

    @Test
    fun aJobWithUnclearOfficeDaysMissesTheOfficeDays() {
        val evaluation =
            evaluation(
                payMatches = true,
                jdMatches = true,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = false,
            )

        assertEquals(PayInformation.KNOWN, evaluation.missingInformation.pay)
        assertEquals(true, evaluation.missingInformation.officeDays)
        assertEquals(true, evaluation.missingInformation.asksAboutOfficeDays)
    }

    @Test
    fun aJobThatMissesThePayAndTheOfficeDaysMissesBoth() {
        val evaluation =
            evaluation(
                payMatches = false,
                jdMatches = true,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = true,
            )

        assertEquals(PayInformation.MISSING, evaluation.missingInformation.pay)
        assertEquals(true, evaluation.missingInformation.officeDays)
        assertEquals(true, evaluation.missingInformation.asksAboutPay)
        assertEquals(true, evaluation.missingInformation.asksAboutOfficeDays)
    }

    private fun evaluation(
        payMatches: Boolean,
        jdMatches: Boolean,
        officeDays: OfficeDays,
        payMissing: Boolean,
    ): JobDescriptionEvaluation.Evaluated =
        JobDescriptionEvaluation.Evaluated(
            payMatches = payMatches,
            jdMatches = jdMatches,
            officeDays = officeDays,
            payMissing = payMissing,
        )

    companion object {
        private val AT_MOST: OfficeDays = OfficeDays.AT_MOST_THE_ALLOWED_DAYS
    }
}
