package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlinx.serialization.Serializable

/**
 * A request to the decision API of Ollama (`POST /v1/systemone`).
 *
 * @property model the name of the decision model, for example `clef:cloud`.
 * @property state the text the questions are asked about.
 * @property questions the named questions about the state.
 */
@Serializable
data class OllamaDecisionRequest(
    val model: String,
    val state: String,
    val questions: Map<String, OllamaDecisionQuestion>,
)

/**
 * A question of a request to the decision API of Ollama.
 *
 * @property type the type of the question: `choice`, `noul` or `score`.
 * @property instructions what the model is asked.
 * @property criteria the options of a `choice` question, the key is the answer
 *   the model gives and the value describes it.
 */
@Serializable
data class OllamaDecisionQuestion(
    val type: String = TYPE_CHOICE,
    val instructions: String,
    val criteria: Map<String, String?>,
) {
    companion object {
        /** The type of a question with named options. */
        const val TYPE_CHOICE: String = "choice"
    }
}
