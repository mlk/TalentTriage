package uk.me.michaellloydlee.recruiter.domain.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals

class OfficeDaysTest {
    @Test
    fun theDaysOfOfficeHaveThreeValues() {
        assertEquals(
            listOf("AT_MOST_THE_ALLOWED_DAYS", "MORE_THAN_THE_ALLOWED_DAYS", "UNCLEAR"),
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
                            payMatches = true,
                            jdMatches = true,
                            officeDays = days,
                            payMissing = false,
                        ).state
                }

        assertEquals(
            mapOf(
                OfficeDays.AT_MOST_THE_ALLOWED_DAYS to EvaluationState.WORTH_FOLLOW_UP,
                OfficeDays.MORE_THAN_THE_ALLOWED_DAYS to EvaluationState.TRASH,
                OfficeDays.UNCLEAR to EvaluationState.NEEDS_MORE_INFORMATION,
            ),
            states,
        )
    }
}
