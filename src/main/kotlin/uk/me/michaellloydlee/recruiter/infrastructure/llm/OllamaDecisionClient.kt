package uk.me.michaellloydlee.recruiter.infrastructure.llm

import io.ktor.client.HttpClient
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.infrastructure.isJson

/**
 * The client of the decision API of Ollama (`POST /v1/systemone`).
 *
 * The decision models answer a typed question about a text: they pick one of
 * the named options and tell how likely every one of them is. The answer is
 * much more predictable than the text of a chat model.
 *
 * Every model call of the pipeline has its own endpoint and its own timeout,
 * the command line fills them in.
 *
 * @param httpClient the client the requests are sent with, it needs the
 *   `HttpTimeout` and the `ContentNegotiation` plugin.
 * @param baseUrl the base URL of the API.
 * @param timeoutMillis how long a call may take, in milliseconds.
 * @param usage counts what the answers took, the counter is shared by the
 *   clients of a run.
 * @param json the parser of the answers of the API.
 */
class OllamaDecisionClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = OllamaChatClient.DEFAULT_BASE_URL,
    private val timeoutMillis: Long = OllamaChatClient.DEFAULT_TIMEOUT_MILLIS,
    private val usage: ModelUsageCounter = ModelUsageCounter(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    init {
        require(httpClient.pluginOrNull(HttpTimeout) != null) {
            "The HttpTimeout plugin has to be installed on the client, otherwise the timeout is ignored"
        }
        require(httpClient.pluginOrNull(ContentNegotiation) != null) {
            "The ContentNegotiation plugin has to be installed on the client, it writes the request"
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
        val body = response.bodyAsText()
        val answer = decode(body, response.contentType(), response.status.value)
        // The answer knows what it took: the tokens of the input and the
        // tokens the model generated.
        usage.record(
            ModelUsage(
                calls = 1,
                inputTokens = answer.usage.inputTokens,
                outputTokens = answer.usage.outputTokens,
            ),
        )
        return answer
    }

    /**
     * The answer of the API.
     *
     * The API answers with a single JSON object. An answer that is not JSON -
     * a model that answers with prose, a broken JSON - is not readable by the
     * client and becomes an [OllamaException] that names the answer.
     */
    private fun decode(
        body: String,
        contentType: ContentType?,
        status: Int,
    ): OllamaDecisionResponse {
        if (!contentType.isJson()) {
            throw OllamaException(
                status,
                "The API answered with $contentType, the client reads a single JSON object",
            )
        }
        return try {
            json.decodeFromString(OllamaDecisionResponse.serializer(), body)
        } catch (error: SerializationException) {
            throw OllamaException.unreadableBody(status, body, error)
        }
    }

    private val decideUrl: String
        get() = "${baseUrl.trimEnd('/')}$DECIDE_PATH"

    companion object {
        /** The path of the decision endpoint of the API. */
        const val DECIDE_PATH: String = "/v1/systemone"
    }
}
