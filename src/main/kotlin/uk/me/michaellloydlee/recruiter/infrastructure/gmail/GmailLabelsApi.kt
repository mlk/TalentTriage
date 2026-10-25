package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import kotlinx.serialization.encodeToString
import uk.me.michaellloydlee.recruiter.domain.gmail.MailboxLabel

/**
 * The label endpoints of the Gmail REST API.
 *
 * @param api the authenticated client of the API.
 */
class GmailLabelsApi(
    private val api: GmailApiClient,
) {
    /**
     * Lists the labels of the mailbox.
     */
    suspend fun listLabels(): List<MailboxLabel> =
        api
            .get(
                "users/me/labels",
                GmailListLabelsResource.serializer(),
            ).labels
            .map { MailboxLabel(id = it.id, name = it.name) }

    /**
     * Creates a label.
     *
     * @param name the full path of the label, for example `Recruiters/Trash`.
     * @return the created label.
     */
    suspend fun createLabel(name: String): MailboxLabel {
        val body = api.json.encodeToString(GmailCreateLabelResource.serializer(), GmailCreateLabelResource(name = name))
        return api.post("users/me/labels", body, GmailLabelResource.serializer()).let {
            MailboxLabel(id = it.id, name = it.name)
        }
    }
}
