package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailSummary
import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The message endpoints of the Gmail REST API.
 *
 * @param api the authenticated client of the API.
 */
class GmailMessagesApi(
    private val api: GmailApiClient,
) {
    /**
     * Lists the messages of the inbox that were not processed yet.
     *
     * The messages are the ones without a state label, the search of the
     * domain describes them. All pages are fetched.
     *
     * @param query the search that selects the messages.
     * @param maxResults the number of messages per page.
     * @return the summaries of the matching messages, the content is fetched
     *   with [getMessage].
     */
    suspend fun listUnprocessed(
        query: String = RecruiterLabel.UNPROCESSED_QUERY,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): List<EmailSummary> {
        val summaries = mutableListOf<EmailSummary>()
        var pageToken: String? = null
        do {
            val page = api.get(messagesUrl(query, maxResults, pageToken), GmailListMessagesResource.serializer())
            summaries.addAll(page.messages.map { EmailSummary(id = it.id, threadId = it.threadId) })
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return summaries
    }

    /**
     * Fetches a message with its content.
     *
     * @param id the id of the message.
     */
    suspend fun getMessage(id: String): EmailMessage {
        val message = api.get("users/me/messages/$id?format=full", GmailMessageResource.serializer())
        return GmailMessageMapper.toDomain(message)
    }

    /**
     * Adds and removes the labels of a message.
     *
     * @param id the id of the message.
     * @param add the ids of the labels the message gets.
     * @param remove the ids of the labels the message loses.
     */
    suspend fun modifyLabels(
        id: String,
        add: List<String>,
        remove: List<String>,
    ) {
        val body =
            api.json.encodeToString(
                GmailModifyLabelsResource.serializer(),
                GmailModifyLabelsResource(addLabelIds = add, removeLabelIds = remove),
            )
        api.post("users/me/messages/$id/modify", body, GmailMessageResource.serializer())
    }

    private fun messagesUrl(
        query: String,
        maxResults: Int,
        pageToken: String?,
    ): String {
        val parameters =
            buildList {
                add("q" to query)
                add("maxResults" to maxResults.toString())
                if (pageToken != null) {
                    add("pageToken" to pageToken)
                }
            }
        val encoded =
            parameters.joinToString(separator = "&") { (name, value) ->
                "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            }
        return "$MESSAGES_PATH?$encoded"
    }

    companion object {
        /** The number of messages the API returns per page by default. */
        const val DEFAULT_MAX_RESULTS: Int = 100

        private const val MESSAGES_PATH = "users/me/messages"
    }
}
