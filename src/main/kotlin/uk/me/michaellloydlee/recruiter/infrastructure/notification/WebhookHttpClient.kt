package uk.me.michaellloydlee.recruiter.infrastructure.notification

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Creates the client of the webhook.
 *
 * The webhook gets a client of its own, separate from the client of the Gmail
 * API and of the models: the webhook must not follow redirects - a `3xx`
 * answer is the answer of the webhook, not a thing to follow - and it has no
 * business with what the other client carries.
 */
object WebhookHttpClient {
    /** Creates the client of the webhook. */
    fun create(): HttpClient =
        HttpClient(CIO) {
            followRedirects = false
            install(HttpTimeout) {
                requestTimeoutMillis = DEFAULT_TIMEOUT_MILLIS
                connectTimeoutMillis = DEFAULT_TIMEOUT_MILLIS
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    /** Timeout of a call of the webhook, in milliseconds. */
    const val DEFAULT_TIMEOUT_MILLIS: Long = 10_000
}
