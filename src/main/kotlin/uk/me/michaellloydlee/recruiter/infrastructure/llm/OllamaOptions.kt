package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The options of a request to an Ollama model.
 *
 * @property temperature how random the answer is.
 * @property topP the nucleus sampling threshold.
 * @property numCtx the size of the context window.
 * @property numPredict the maximum number of tokens of the answer.
 * @property seed the seed of the sampler, the same seed gives the same answer.
 */
@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("num_ctx")
    val numCtx: Int? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    val seed: Int? = null,
)
