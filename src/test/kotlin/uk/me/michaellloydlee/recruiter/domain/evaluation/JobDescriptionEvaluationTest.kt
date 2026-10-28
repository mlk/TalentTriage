package uk.me.michaellloydlee.recruiter.domain.evaluation

import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class JobDescriptionEvaluationTest {
    @Test
    fun aJobThatMatchesIsWorthAFollowUp() {
        val evaluation = evaluation(payMatches = true, jdMatches = true, officeDaysMatch = true, payMissing = false)

        assertEquals(RecruiterLabel.WORTH_FOLLOW_UP, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashed() {
        val evaluation = evaluation(payMatches = true, jdMatches = false, officeDaysMatch = true, payMissing = false)

        assertEquals(RecruiterLabel.TRASH, evaluation.state)
    }

    @Test
    fun aJobThatDemandsTooMuchOfficeIsTrashed() {
        val evaluation = evaluation(payMatches = true, jdMatches = true, officeDaysMatch = false, payMissing = false)

        assertEquals(RecruiterLabel.TRASH, evaluation.state)
    }

    @Test
    fun aJobWithoutAPayNeedsThePayChecked() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDaysMatch = true, payMissing = true)

        assertEquals(RecruiterLabel.NEEDS_PAY_CHECK, evaluation.state)
    }

    @Test
    fun aJobWithATooLowPayNeedsThePayChecked() {
        val evaluation = evaluation(payMatches = false, jdMatches = true, officeDaysMatch = true, payMissing = false)

        assertEquals(RecruiterLabel.NEEDS_PAY_CHECK, evaluation.state)
    }

    @Test
    fun aJobThatDoesNotMatchIsTrashedEvenWithoutAPay() {
        val evaluation = evaluation(payMatches = false, jdMatches = false, officeDaysMatch = true, payMissing = true)

        assertEquals(RecruiterLabel.TRASH, evaluation.state)
    }

    private fun evaluation(
        payMatches: Boolean,
        jdMatches: Boolean,
        officeDaysMatch: Boolean,
        payMissing: Boolean,
    ): JobDescriptionEvaluation.Evaluated =
        JobDescriptionEvaluation.Evaluated(
            payMatches = payMatches,
            jdMatches = jdMatches,
            officeDaysMatch = officeDaysMatch,
            payMissing = payMissing,
        )
}
