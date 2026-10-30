package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailDraft

/**
 * The draft endpoints of the Gmail REST API.
 *
 * A draft is a mail of the mailbox that is not sent: it waits in the drafts of
 * the job seeker until the job seeker sends it. The API takes the mail of a
 * draft as it is written on the wire, encoded with base64url.
 *
 * @param api the authenticated client of the API.
 */
class GmailDraftsApi(
    private val api: GmailApiClient,
) {
    /**
     * Writes a draft into the mailbox.
     *
     * @param draft the draft to write.
     * @return the id of the draft.
     */
    suspend fun createDraft(draft: EmailDraft): String {
        val body =
            api.json.encodeToString(
                GmailDraftResource.serializer(),
                GmailDraftResource(
                    message =
                        GmailDraftMessageResource(
                            raw = MimeMessage.encode(draft),
                            threadId = draft.threadId,
                        ),
                ),
            )
        return api.post(DRAFTS_PATH, body, GmailDraftResource.serializer()).id
    }

    companion object {
        /** The path of the endpoint that creates a draft. */
        const val DRAFTS_PATH: String = "users/me/drafts"
    }
}
