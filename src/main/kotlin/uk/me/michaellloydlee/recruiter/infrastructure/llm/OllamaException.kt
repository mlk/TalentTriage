package uk.me.michaellloydlee.recruiter.infrastructure.llm

import uk.me.michaellloydlee.recruiter.infrastructure.bodySnippet

/**
 * Raised when an Ollama API request fails.
 *
 * @property statusCode the HTTP status code of the response.
 * @property body the body of the response, it holds the error of Ollama.
 * @param cause the exception that caused the failure.
 */
class OllamaException(
    val statusCode: Int,
    val body: String,
    cause: Exception? = null,
) : RuntimeException("The Ollama API request failed with status $statusCode: $body", cause) {
    companion object {
        /**
         * The exception for an answer that is not the JSON the client reads.
         *
         * The body of the answer is part of the message: a model that answers
         * with prose or with a broken JSON is the reason the mail waits for the
         * next run, and the log has to say what the model said.
         *
         * @param statusCode the HTTP status code of the response.
         * @param body the body of the answer.
         * @param cause the exception the parser raised.
         */
        fun unreadableBody(
            statusCode: Int,
            body: String,
            cause: Exception?,
        ): OllamaException =
            OllamaException(
                statusCode,
                "The API answered with a body that is not the JSON the client reads: ${bodySnippet(body)}",
                cause,
            )
    }
}
