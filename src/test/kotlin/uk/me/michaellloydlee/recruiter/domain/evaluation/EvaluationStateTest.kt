package uk.me.michaellloydlee.recruiter.domain.evaluation

import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluationStateTest {
    @Test
    fun everyStateCarriesTheLabelOfTheMail() {
        assertEquals(RecruiterLabel.WORTH_FOLLOW_UP, EvaluationState.WORTH_FOLLOW_UP.label)
        assertEquals(RecruiterLabel.NEEDS_MORE_INFORMATION, EvaluationState.NEEDS_MORE_INFORMATION.label)
        assertEquals(RecruiterLabel.TRASH, EvaluationState.TRASH.label)
    }

    @Test
    fun noStateIsAJobListing() {
        assertTrue(EvaluationState.entries.none { it.label == RecruiterLabel.JOB_LISTING })
    }
}
