package uk.me.michaellloydlee.recruiter.infrastructure.llm

/**
 * What the calls to the models took.
 *
 * @property calls how many times the models answered.
 * @property inputTokens the tokens of the input of the models.
 * @property outputTokens the tokens the models generated.
 */
data class ModelUsage(
    val calls: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
) {
    /**
     * The usage of two sets of calls added together.
     *
     * @param other the usage of the other calls.
     * @return the usage of both.
     */
    operator fun plus(other: ModelUsage): ModelUsage =
        ModelUsage(
            calls = calls + other.calls,
            inputTokens = inputTokens + other.inputTokens,
            outputTokens = outputTokens + other.outputTokens,
        )

    companion object {
        /** The usage of a run that did not call a model. */
        val EMPTY: ModelUsage = ModelUsage()
    }
}

/**
 * Counts what the calls to the models took.
 *
 * Every client of a model tells the counter what its answer took. The
 * counter is shared by the clients of a run: the command line creates it and
 * gives it to every client of the pipeline.
 *
 * The pipeline asks the models one after the other, the counter needs no
 * lock.
 */
class ModelUsageCounter {
    private var usage: ModelUsage = ModelUsage.EMPTY

    /**
     * Adds the usage of one answer to the count.
     *
     * @param usage what the answer took.
     */
    fun record(usage: ModelUsage) {
        this.usage = this.usage + usage
    }

    /**
     * What the calls took until now.
     *
     * @return the usage of the calls that were made.
     */
    fun usage(): ModelUsage = usage
}
