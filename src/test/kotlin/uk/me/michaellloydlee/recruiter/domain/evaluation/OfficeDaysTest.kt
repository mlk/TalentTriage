package uk.me.michaellloydlee.recruiter.domain.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals

class OfficeDaysTest {
    @Test
    fun theDaysOfOfficeHaveFourValues() {
        assertEquals(
            listOf(
                "AT_MOST_THE_ALLOWED_DAYS",
                "MORE_THAN_THE_ALLOWED_DAYS",
                "UNCLEAR",
                "NOT_KNOWN",
            ),
            OfficeDays.entries.map { it.name },
        )
    }

    @Test
    fun everyValueOfTheDaysOfOfficeLeadsToAState() {
        val states =
            OfficeDays.entries
                .associateWith { days ->
                    JobDescriptionEvaluation
                        .Evaluated(
                            jdMatch = JobMatch.MATCHES,
                            officeDays = days,
                            payMatch = PayMatch.MEETS_THE_TARGET,
                        ).state
                }

        assertEquals(
            mapOf(
                OfficeDays.AT_MOST_THE_ALLOWED_DAYS to EvaluationState.WORTH_FOLLOW_UP,
                OfficeDays.MORE_THAN_THE_ALLOWED_DAYS to EvaluationState.TRASH,
                OfficeDays.UNCLEAR to EvaluationState.NEEDS_MORE_INFORMATION,
                OfficeDays.NOT_KNOWN to EvaluationState.NEEDS_MORE_INFORMATION,
            ),
            states,
        )
    }
}
