package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Creates the Ktor client that talks to Google.
 *
 * The client is configured for the two APIs the application uses: the OAuth2
 * token endpoint and the Gmail REST API. Failures are not turned into
 * exceptions by the client itself, the callers inspect the status code.
 */
object GmailHttpClientFactory {
    /** Timeout of the requests to Google, in milliseconds. */
    const val DEFAULT_TIMEOUT_MILLIS: Long = 30_000

    /**
     * Creates the client.
     *
     * @param jsonFormat parser used for the JSON payloads.
     * @param engine the engine the client uses, the CIO engine by default.
     */
    fun create(
        jsonFormat: Json = Json { ignoreUnknownKeys = true },
        engine: HttpClientEngine = CIO.create(),
    ): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(jsonFormat)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = DEFAULT_TIMEOUT_MILLIS
                connectTimeoutMillis = DEFAULT_TIMEOUT_MILLIS
            }
        }
}
