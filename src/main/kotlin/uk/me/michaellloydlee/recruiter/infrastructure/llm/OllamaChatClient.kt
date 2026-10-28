package uk.me.michaellloydlee.recruiter.infrastructure.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * The client of the chat endpoint of an Ollama model.
 *
 * The client talks to the native Ollama API. The endpoint and the timeout
 * are the settings of the call, every model call of the pipeline has its own:
 * the screening of the recruiter mails is quick, the evaluation of the job
 * descriptions needs minutes.
 *
 * @param httpClient the client the requests are sent with, it needs the
 *   `HttpTimeout` and the `ContentNegotiation` plugin.
 * @param baseUrl the base URL of the API.
 * @param timeoutMillis how long a call may take, in milliseconds.
 */
class OllamaChatClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(httpClient.pluginOrNull(HttpTimeout) != null) {
            "The HttpTimeout plugin has to be installed on the client, otherwise the timeout is ignored"
        }
        require(httpClient.pluginOrNull(ContentNegotiation) != null) {
            "The ContentNegotiation plugin has to be installed on the client, " +
                "it writes the request and reads the answer"
        }
    }

    /**
     * Sends a chat request to the model and waits for the whole answer.
     *
     * @param request the request with the model and the conversation.
     * @return the answer of the model.
     * @throws IllegalArgumentException when the request asks for a streamed
     *   answer, the client cannot read one.
     * @throws OllamaException when the API answers with an error.
     */
    suspend fun chat(request: OllamaChatRequest): OllamaChatResponse {
        require(!request.stream) {
            "The client reads the whole answer at once, the API must not stream it"
        }
        val response =
            httpClient.post(chatUrl) {
                expectSuccess = false
                timeout { requestTimeoutMillis = this@OllamaChatClient.timeoutMillis }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        if (!response.status.isSuccess()) {
            throw OllamaException(response.status.value, response.bodyAsText())
        }
        return try {
            response.body()
        } catch (error: NoTransformationFoundException) {
            // The API streamed the answer although the request asked for the
            // whole one, the client cannot read a stream.
            throw OllamaException(
                response.status.value,
                "The API answered with ${response.contentType()}, the client reads a single JSON object",
                error,
            )
        }
    }

    private val chatUrl: String
        get() = "${baseUrl.trimEnd('/')}$CHAT_PATH"

    companion object {
        /** The base URL of an Ollama instance. */
        const val DEFAULT_BASE_URL: String = "http://localhost:11434"

        /** How long a call may take, in milliseconds. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 120_000

        /** The path of the chat endpoint of the API. */
        const val CHAT_PATH: String = "/api/chat"
    }
}
