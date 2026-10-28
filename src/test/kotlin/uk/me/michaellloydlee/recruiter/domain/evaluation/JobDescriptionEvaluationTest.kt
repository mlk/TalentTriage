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
    fun aJobWithoutAPayNeedsThePayChecked() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = true)

        assertEquals(RecruiterLabel.NEEDS_PAY_CHECK, evaluation.state)
    }

    @Test
    fun aJobWithATooLowPayNeedsThePayChecked() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDays = AT_MOST, payMissing = false)

        assertEquals(RecruiterLabel.NEEDS_PAY_CHECK, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashedEvenWithoutAPay() {
        val evaluation = evaluation(payMatches = false, jdMatches = false, officeDays = AT_MOST, payMissing = true)

        assertEquals(RecruiterLabel.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithUnclearOfficeDaysNeedsTheWorkFromHomeCheck() {
        val evaluation =
            evaluation(
                payMatches = true,
                jdMatches = true,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = false,
            )

        assertEquals(RecruiterLabel.NEEDS_WORK_FROM_HOME_CHECK, evaluation.state)
    }

    @Test
    fun aJobWithUnclearOfficeDaysNeedsTheWorkFromHomeCheckEvenWithoutAPay() {
        val evaluation =
            evaluation(
                payMatches = false,
                jdMatches = true,
                officeDays = OfficeDays.UNCLEAR,
                payMissing = true,
            )

        assertEquals(RecruiterLabel.NEEDS_WORK_FROM_HOME_CHECK, evaluation.state)
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
