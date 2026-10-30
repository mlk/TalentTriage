package uk.me.michaellloydlee.recruiter.infrastructure.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException

/**
 * The client of the web search of Ollama (`POST /api/web_search`).
 *
 * The web search is the way of the application to the pages of the web: it
 * looks for the pay of a role in a region. The search needs the API key of
 * Ollama, without a key the search is not made and the caller decides what it
 * does without the pages.
 *
 * @param httpClient the client the requests are sent with, it needs the
 *   `HttpTimeout` and the `ContentNegotiation` plugin.
 * @param apiKey the API key of Ollama, `null` when the search is not possible.
 * @param baseUrl the base URL of the API.
 * @param timeoutMillis how long a call may take, in milliseconds.
 */
class OllamaWebSearchClient(
    private val httpClient: HttpClient,
    private val apiKey: String? = null,
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
     * Searches the web and waits for the whole answer.
     *
     * @param query what is searched for.
     * @param maxResults how many pages the search returns.
     * @return the pages of the search, the failure when the search was not
     *   possible.
     */
    suspend fun search(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): WebSearchOutcome {
        val key = apiKey
        if (key == null) {
            return WebSearchOutcome.Failed(MISSING_KEY)
        }
        return try {
            val response =
                httpClient.post(searchUrl) {
                    expectSuccess = false
                    timeout { requestTimeoutMillis = this@OllamaWebSearchClient.timeoutMillis }
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $key")
                    setBody(OllamaWebSearchRequest(query = query, maxResults = maxResults))
                }
            if (!response.status.isSuccess()) {
                WebSearchOutcome.Failed(
                    "The web search of Ollama answered with ${response.status.value}: ${response.bodyAsText()}",
                )
            } else {
                WebSearchOutcome.Found(response.body<OllamaWebSearchResponse>().results)
            }
        } catch (error: NoTransformationFoundException) {
            WebSearchOutcome.Failed(
                "The web search of Ollama answered with ${error.message}, it reads a single JSON object",
            )
        } catch (error: IOException) {
            WebSearchOutcome.Failed("The web search of Ollama could not be reached: ${error.message}")
        }
    }

    private val searchUrl: String
        get() = "${baseUrl.trimEnd('/')}$SEARCH_PATH"

    companion object {
        /** The base URL of the cloud of Ollama. */
        const val DEFAULT_BASE_URL: String = "https://ollama.com"

        /** How long a call may take, in milliseconds. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 30_000

        /** The path of the web search of the API. */
        const val SEARCH_PATH: String = "/api/web_search"

        /** How many pages a search returns by default. */
        const val DEFAULT_MAX_RESULTS: Int = 5

        /** The reason of a search without an API key. */
        const val MISSING_KEY: String =
            "The web search of Ollama needs an API key, set OLLAMA_API_KEY to use it"
    }
}
