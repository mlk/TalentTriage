package uk.me.michaellloydlee.recruiter.infrastructure.llm

import kotlinx.serialization.Serializable

/**
 * A message of a chat with an Ollama model.
 *
 * @property role the role of the speaker: `system`, `user` or `assistant`.
 * @property content the text of the message.
 */
@Serializable
data class OllamaMessage(
    val role: String,
    val content: String,
) {
    companion object {
        /** The role of the instructions of the system. */
        const val ROLE_SYSTEM: String = "system"

        /** The role of the person the model talks to. */
        const val ROLE_USER: String = "user"

        /** The role of the model. */
        const val ROLE_ASSISTANT: String = "assistant"
    }
}
