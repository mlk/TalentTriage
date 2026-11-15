package uk.me.michaellloydlee.recruiter.infrastructure.notification

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

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
                WebhookOutcome.Failed("The webhook answered with ${response.status.value}: ${errorBody(response)}")
            } else {
                WebhookOutcome.Sent
            }
        } catch (error: CancellationException) {
            // A cancellation of the caller belongs to the caller: it goes on.
            // A cancellation that is only the timeout of the request is not a
            // cancellation of the run, the webhook is reported as it could
            // not be reached.
            coroutineContext.ensureActive()
            WebhookOutcome.Failed("The webhook could not be reached: ${reasonOf(error)}")
        } catch (error: Exception) {
            // Broad on purpose: whatever the client throws, the webhook is
            // not allowed to take the run down with it. A timeout surfaces
            // here as an IOException.
            WebhookOutcome.Failed("The webhook could not be reached: ${reasonOf(error)}")
        }

    /**
     * Reads at most [MAX_ERROR_BODY_LENGTH] bytes of the error body, a larger
     * body is truncated and is never buffered whole. A body that cannot be
     * read does not take the status code down with it: the code is the reason
     * the job seeker needs, the body is a detail.
     */
    @Suppress("TooGenericExceptionCaught") // A body that fails to read must not mask the status code.
    private suspend fun errorBody(response: HttpResponse): String =
        try {
            val bytes = ByteArray(MAX_ERROR_BODY_LENGTH)
            val buffer = ByteBuffer.wrap(bytes)
            val channel = response.bodyAsChannel()
            while (buffer.hasRemaining()) {
                val read = channel.readAvailable(buffer)
                if (read <= 0) {
                    break
                }
            }
            String(bytes, 0, bytes.size - buffer.remaining(), Charsets.UTF_8)
        } catch (error: Exception) {
            "the body could not be read (${error.message ?: "no details"})"
        }

    private fun reasonOf(error: Throwable): String = "${error::class.simpleName}: ${error.message ?: "no details"}"

    companion object {
        /** How long a call may take, in milliseconds. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 10_000

        /** How many bytes of the error body of the webhook go into the failure reason. */
        const val MAX_ERROR_BODY_LENGTH: Int = 512
    }
}
