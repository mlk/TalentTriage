package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The answer of an Ollama model to a chat request.
 *
 * @property model the model that answered.
 * @property createdAt the moment the answer was created.
 * @property message the answer of the model.
 * @property done whether the model is done, `false` while the answer streams.
 * @property totalDuration how long the whole call took, in nanoseconds.
 * @property evalCount the number of tokens of the answer.
 * @property promptEvalCount the number of tokens of the prompt.
 */
@Serializable
data class OllamaChatResponse(
    val model: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    val message: OllamaMessage? = null,
    val done: Boolean = true,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
) {
    /**
     * The text of the answer, empty when the model did not answer.
     */
    val content: String
        get() = message?.content.orEmpty()
}
