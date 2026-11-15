package uk.me.michaellloydlee.recruiter.infrastructure.notification

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
import kotlinx.coroutines.CancellationException

/**
 * The client of the webhook the run calls when a mail needs the job seeker.
 *
 * The webhook is how the run tells the job seeker about the mails that wait
 * for him: one call per run, with a JSON body that holds the mails that need
 * him. A webhook that cannot be reached or that answers with an error is not
 * a failure of the run, it is a value the caller logs and moves on from.
 *
 * @param httpClient the client the requests are sent with, it needs the
 *   `HttpTimeout` and the `ContentNegotiation` plugin, the plugin writes the
 *   request.
 * @param url the URL of the webhook.
 * @param timeoutMillis how long a call may take, in milliseconds.
 */
class WebhookNotifier(
    private val httpClient: HttpClient,
    private val url: String,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
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
     * Calls the webhook with the mails that need the job seeker.
     *
     * @param mails the mails that need the job seeker.
     * @return what happened to the call.
     */
    @Suppress("TooGenericExceptionCaught") // The webhook must not take the run down with it.
    suspend fun notify(mails: List<NotificationMail>): WebhookOutcome =
        try {
            val response =
                httpClient.post(url) {
                    expectSuccess = false
                    timeout { requestTimeoutMillis = this@WebhookNotifier.timeoutMillis }
                    contentType(ContentType.Application.Json)
                    setBody(WebhookNotification(mails))
                }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText().take(MAX_ERROR_BODY_LENGTH)
                WebhookOutcome.Failed("The webhook answered with ${response.status.value}: $body")
            } else {
                WebhookOutcome.Sent
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Broad on purpose: whatever the client throws, the webhook is
            // not allowed to take the run down with it. A timeout surfaces
            // here as an IOException.
            WebhookOutcome.Failed("The webhook could not be reached: ${error.message}")
        }

    companion object {
        /** How long a call may take, in milliseconds. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 10_000

        /** How many characters of the error body of the webhook go into the failure reason. */
        const val MAX_ERROR_BODY_LENGTH: Int = 512
    }
}
