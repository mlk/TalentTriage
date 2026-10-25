package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import io.ktor.client.HttpClient
import java.io.Closeable

/**
 * The Gmail components of the application, assembled for a run.
 *
 * @param httpClient the client the API calls are sent with.
 * @param messages the message endpoints of the API.
 * @param labels the label endpoints of the API.
 * @param stateManager the state machine of the messages.
 */
class GmailServices private constructor(
    private val httpClient: HttpClient,
    val messages: GmailMessagesApi,
    val labels: GmailLabelsApi,
    val stateManager: GmailLabelStateManager,
) : Closeable {
    /**
     * Closes the client, the components cannot be used afterwards.
     */
    override fun close() {
        httpClient.close()
    }

    companion object {
        /**
         * Assembles the components.
         *
         * @param credentials the OAuth2 client credentials of the application.
         * @param tokenStore store the OAuth2 token is read from and written to.
         * @param httpClient the client the API calls are sent with.
         */
        fun create(
            credentials: GoogleClientCredentials,
            tokenStore: OAuth2TokenStore,
            httpClient: HttpClient,
        ): GmailServices {
            val tokens =
                RefreshingAccessTokenProvider(
                    refresher = GmailOAuth2Client(httpClient, credentials),
                    tokenStore = tokenStore,
                )
            val api = GmailApiClient(httpClient, tokens)
            val messages = GmailMessagesApi(api)
            val labels = GmailLabelsApi(api)
            return GmailServices(
                httpClient = httpClient,
                messages = messages,
                labels = labels,
                stateManager = GmailLabelStateManager(messages, labels),
            )
        }
    }
}
