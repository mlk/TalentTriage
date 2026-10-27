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
 * The client of the decision API of Ollama (`POST /v1/systemone`).
 *
 * The decision models answer a typed question about a text: they pick one of
 * the named options and tell how likely every one of them is. The answer is
 * much more predictable than the text of a chat model.
 *
 * @param httpClient the client the requests are sent with, it needs the
 *   `HttpTimeout` and the `ContentNegotiation` plugin.
 * @param baseUrl the base URL of the API, the local Ollama by default.
 * @param timeoutMillis how long a call may take, in milliseconds.
 */
class OllamaDecisionClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = OllamaChatClient.DEFAULT_BASE_URL,
    private val timeoutMillis: Long = OllamaChatClient.DEFAULT_TIMEOUT_MILLIS,
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
     * Asks the decision model and returns its answer.
     *
     * @param request the request with the model, the state and the questions.
     * @return the answer of the model.
     * @throws OllamaException when the API answers with an error.
     */
    suspend fun decide(request: OllamaDecisionRequest): OllamaDecisionResponse {
        val response =
            httpClient.post(decideUrl) {
                expectSuccess = false
                timeout { requestTimeoutMillis = this@OllamaDecisionClient.timeoutMillis }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        if (!response.status.isSuccess()) {
            throw OllamaException(response.status.value, response.bodyAsText())
        }
        return try {
            response.body()
        } catch (error: NoTransformationFoundException) {
            throw OllamaException(
                response.status.value,
                "The API answered with ${response.contentType()}, the client reads a single JSON object",
                error,
            )
        }
    }

    private val decideUrl: String
        get() = "${baseUrl.trimEnd('/')}$DECIDE_PATH"

    companion object {
        /** The path of the decision endpoint of the API. */
        const val DECIDE_PATH: String = "/v1/systemone"
    }
}
