package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlinx.serialization.Serializable

/**
 * A request to the chat endpoint of an Ollama model.
 *
 * @property model the name of the model, for example `llama3.2:3b`.
 * @property messages the conversation so far.
 * @property stream whether the answer is streamed, the client asks for the
 *   whole answer at once.
 * @property options the options of the model.
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null,
)
