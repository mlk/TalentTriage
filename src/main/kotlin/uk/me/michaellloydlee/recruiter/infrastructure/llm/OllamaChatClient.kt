package uk.me.michaellloydlee.recruiter.infrastructure.llm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The client of the chat endpoint of an Ollama model.
 *
 * The client talks to the native Ollama API, so the same class serves the
 * local Ollama instance and the AirLLM wrapper - only the base URL and the
 * timeout are different. The local model answers in seconds and gets a short
 * timeout, the deep evaluation model needs minutes and gets a long one.
 *
 * @param httpClient the client the requests are sent with, it needs the
 *   `HttpTimeout` plugin, otherwise the timeout of the request is ignored.
 * @param baseUrl the base URL of the API, the local Ollama by default.
 * @param timeoutMillis how long a call may take, in milliseconds.
 * @param json encoder and parser of the payloads.
 */
class OllamaChatClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            // The defaults are written on purpose: the request has to carry
            // "stream": false, so that the API answers with the whole text
            // instead of a stream.
            encodeDefaults = true
            explicitNulls = false
        },
) {
    init {
        require(httpClient.pluginOrNull(HttpTimeout) != null) {
            "The HttpTimeout plugin has to be installed on the client, otherwise the timeout is ignored"
        }
    }

    /**
     * Sends a chat request to the model and waits for the whole answer.
     *
     * @param request the request with the model and the conversation.
     * @return the answer of the model.
     * @throws OllamaException when the API answers with an error.
     */
    suspend fun chat(request: OllamaChatRequest): OllamaChatResponse {
        val response =
            httpClient.post(chatUrl) {
                expectSuccess = false
                timeout { requestTimeoutMillis = this@OllamaChatClient.timeoutMillis }
                contentType(ContentType.Application.Json)
                // The body is written by hand instead of by the content
                // negotiation of Ktor: the client brings its own encoder, so
                // the payload does not depend on the configuration of the
                // HTTP client it was given.
                setBody(json.encodeToString(OllamaChatRequest.serializer(), request))
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw OllamaException(response.status.value, body)
        }
        return json.decodeFromString(OllamaChatResponse.serializer(), body)
    }

    private val chatUrl: String
        get() = "${baseUrl.trimEnd('/')}$CHAT_PATH"

    companion object {
        /** The base URL of a local Ollama instance. */
        const val DEFAULT_BASE_URL: String = "http://localhost:11434"

        /** The base URL of the AirLLM wrapper. */
        const val DEEP_EVAL_BASE_URL: String = "http://localhost:8000"

        /** How long a call to the local model may take, in milliseconds. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 120_000

        /** How long a call to the deep evaluation model may take, in milliseconds. */
        const val DEEP_EVAL_TIMEOUT_MILLIS: Long = 600_000

        /** The path of the chat endpoint of the API. */
        const val CHAT_PATH: String = "/api/chat"
    }
}
