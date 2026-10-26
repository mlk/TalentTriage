package uk.me.michaellloydlee.recruiter.infrastructure.llm

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
) : RuntimeException("The Ollama API request failed with status $statusCode: $body", cause)
