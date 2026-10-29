package uk.me.michaellloydlee.recruiter.domain.evaluation

import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class JobDescriptionEvaluationTest {
    @Test
    fun aJobThatMatchesIsWorthAFollowUp() {
        val evaluation = evaluation(payMatches = true, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(RecruiterLabel.WORTH_FOLLOW_UP, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashed() {
        val evaluation = evaluation(payMatches = true, jdMatches = false, officeDays = AT_MOST, payMissing = false)

        assertEquals(RecruiterLabel.TRASH, evaluation.state)
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

        assertEquals(RecruiterLabel.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithoutAPayNeedsMoreInformation() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = true)

        assertEquals(RecruiterLabel.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobWithATooLowPayNeedsMoreInformation() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(RecruiterLabel.NEEDS_MORE_INFORMATION, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashedEvenWithoutAPay() {
        val evaluation = evaluation(payMatches = false, jdMatches = false, officeDays = AT_MOST, payMissing = true)

        assertEquals(RecruiterLabel.TRASH, evaluation.state)
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

        assertEquals(RecruiterLabel.NEEDS_MORE_INFORMATION, evaluation.state)
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

        assertEquals(RecruiterLabel.NEEDS_MORE_INFORMATION, evaluation.state)
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

        assertEquals(RecruiterLabel.TRASH, evaluation.state)
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
    fun theTooLowPayIsNamed() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals("the pay is below the target", evaluation.missing)
    }

    @Test
    fun anEvaluationTheModelCouldNotMakeNeedsMoreInformation() {
        val evaluation = JobDescriptionEvaluation.Unknown("The decision model could not be reached")

        assertEquals(RecruiterLabel.NEEDS_MORE_INFORMATION, evaluation.state)
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
