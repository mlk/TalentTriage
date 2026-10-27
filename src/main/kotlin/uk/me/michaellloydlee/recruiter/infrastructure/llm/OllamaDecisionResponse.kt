package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The answer of the decision API of Ollama.
 *
 * @property model the model that answered.
 * @property answers the answers of the questions, keyed by their name.
 * @property usage the tokens the request took.
 */
@Serializable
data class OllamaDecisionResponse(
    val model: String = "",
    val answers: Map<String, OllamaDecisionAnswer> = emptyMap(),
    val usage: OllamaDecisionUsage = OllamaDecisionUsage(),
)

/**
 * The answer of one question of the decision API of Ollama.
 *
 * @property type the type of the question the answer belongs to.
 * @property choice the option the model chose.
 * @property noul the probability of `true` of a yes or no answer.
 * @property probabilities the probability of every option, they sum up to one.
 * @property confidence how strongly the model favours the option, zero is a
 *   uniform distribution and near one is a clear winner. It is not a
 *   calibrated correctness.
 */
@Serializable
data class OllamaDecisionAnswer(
    val type: String = "",
    val choice: String? = null,
    val noul: Double? = null,
    val probabilities: Map<String, Double> = emptyMap(),
    val confidence: Double? = null,
)

/**
 * The tokens a request to the decision API of Ollama took.
 *
 * @property inputTokens the tokens of the input, the state is counted again
 *   for every question the model scores on its own.
 * @property outputTokens the tokens the model generated while scoring.
 */
@Serializable
data class OllamaDecisionUsage(
    @SerialName("input_tokens")
    val inputTokens: Long = 0,
    @SerialName("output_tokens")
    val outputTokens: Long = 0,
)
