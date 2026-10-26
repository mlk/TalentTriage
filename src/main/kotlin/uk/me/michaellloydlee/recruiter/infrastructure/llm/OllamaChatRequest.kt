package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlinx.serialization.Serializable

/**
 * A request to the chat endpoint of an Ollama model.
 *
 * @property model the name of the model, for example `qwen:1.8b`.
 * @property messages the conversation so far.
 * @property stream whether the answer is streamed. The field has no default on
 *   purpose: the API streams by default, and a field with a default is left
 *   out of the payload when the encoder does not write the defaults.
 * @property options the options of the model.
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean,
    val options: OllamaOptions? = null,
)
