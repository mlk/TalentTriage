package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelUsageTest {
    @Test
    fun addsTheUsageOfTheCallsTogether() {
        val usage = ModelUsageCounter()

        usage.record(ModelUsage(calls = 1, inputTokens = 10, outputTokens = 2))
        usage.record(ModelUsage(calls = 1, inputTokens = 5, outputTokens = 1))

        assertEquals(ModelUsage(calls = 2, inputTokens = 15, outputTokens = 3), usage.usage())
    }

    @Test
    fun countsNothingBeforeTheFirstCall() {
        assertEquals(ModelUsage.EMPTY, ModelUsageCounter().usage())
    }

    @Test
    fun countsTheCallsOfMoreThanOneThread() {
        val counter = ModelUsageCounter()
        val threads =
            (1..4).map {
                Thread {
                    repeat(100) {
                        counter.record(ModelUsage(calls = 1, inputTokens = 2, outputTokens = 3))
                    }
                }
            }

        threads.forEach { thread -> thread.start() }
        threads.forEach { thread -> thread.join() }

        assertEquals(ModelUsage(calls = 400, inputTokens = 800, outputTokens = 1200), counter.usage())
    }
}
