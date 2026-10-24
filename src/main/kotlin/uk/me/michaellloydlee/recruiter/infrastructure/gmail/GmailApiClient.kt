package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * The client of the Gmail REST API.
 *
 * Every request is sent with the bearer token of the [tokens] provider. When
 * the API rejects the token with a `401`, the token is refreshed and the
 * request is sent again.
 *
 * @param httpClient the client used for the requests.
 * @param tokens provider of the access token.
 * @param baseUrl the base URL of the Gmail REST API.
 * @param json parser used for the responses.
 */
class GmailApiClient(
    private val httpClient: HttpClient,
    private val tokens: AccessTokenProvider,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * Sends a `GET` request and decodes the answer.
     *
     * @param path path of the resource, relative to the base URL.
     * @param deserializer parser of the answer.
     */
    suspend fun <T> get(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): T =
        execute(deserializer) {
            method = HttpMethod.Get
            url(resolve(path))
        }

    /**
     * Sends a `POST` request with a JSON body and decodes the answer.
     *
     * @param path path of the resource, relative to the base URL.
     * @param body the JSON body of the request.
     * @param deserializer parser of the answer.
     */
    suspend fun <T> post(
        path: String,
        body: String,
        deserializer: DeserializationStrategy<T>,
    ): T =
        execute(deserializer) {
            method = HttpMethod.Post
            url(resolve(path))
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun <T> execute(
        deserializer: DeserializationStrategy<T>,
        request: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): T {
        val response = send(request, tokens.accessToken())
        val authenticated =
            if (response.status == HttpStatusCode.Unauthorized) {
                send(request, tokens.refresh())
            } else {
                response
            }
        val body = authenticated.bodyAsText()
        if (!authenticated.status.isSuccess()) {
            throw GmailApiException(authenticated.status.value, body)
        }
        return json.decodeFromString(deserializer, body)
    }

    private suspend fun send(
        request: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
        accessToken: String,
    ): HttpResponse =
        httpClient.request {
            expectSuccess = false
            request()
            bearerAuth(accessToken)
        }

    private fun resolve(path: String): String = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    companion object {
        /** The base URL of the Gmail REST API. */
        const val DEFAULT_BASE_URL: String = "https://gmail.googleapis.com/gmail/v1"
    }
}
