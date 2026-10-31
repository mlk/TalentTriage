package uk.me.michaellloydlee.recruiter.infrastructure.gmail

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import uk.me.michaellloydlee.recruiter.domain.gmail.transition

/**
 * Keeps the state of the messages of the mailbox.
 *
 * The state of a message is the label it carries. Applying a state label
 * removes the other state labels, so a message carries exactly one state, and
 * a message whose state archives it loses the inbox label as well.
 *
 * The state labels are created when they do not exist yet.
 *
 * @param messages the message endpoints of the API.
 * @param labels the label endpoints of the API.
 */
class GmailLabelStateManager(
    private val messages: GmailMessagesApi,
    private val labels: GmailLabelsApi,
) {
    private val labelIds = mutableMapOf<RecruiterLabel, String>()

    /**
     * Applies the state label to a message.
     *
     * @param messageId the id of the message.
     * @param label the state the message changes to.
     */
    suspend fun apply(
        messageId: String,
        label: RecruiterLabel,
    ) {
        val transition = label.transition(stateLabelIds(), INBOX_LABEL_ID)
        messages.modifyLabels(messageId, transition.add, transition.remove)
    }

    /**
     * Removes the state of a message.
     *
     * The message carries no state label afterwards and it is in the inbox
     * again: it is pending, the run that looks at it decides its state. A
     * message that carries no state label is left alone.
     *
     * @param message the message to clear.
     */
    suspend fun clear(message: EmailMessage) {
        val state = stateLabelIds().filterValues { it in message.labelIds }.values.toList()
        if (state.isEmpty()) {
            return
        }
        messages.modifyLabels(
            message.id,
            add = listOf(INBOX_LABEL_ID),
            remove = state,
        )
    }

    /**
     * The ids of the state labels, the missing ones are created.
     */
    private suspend fun stateLabelIds(): Map<RecruiterLabel, String> {
        val missing = RecruiterLabel.entries.filter { it !in labelIds }
        if (missing.isNotEmpty()) {
            val existing = labels.listLabels().associateBy { it.name }
            for (label in missing) {
                val mailboxLabel = existing[label.path] ?: labels.createLabel(label.path)
                labelIds[label] = mailboxLabel.id
            }
        }
        return labelIds
    }

    companion object {
        /** The id of the inbox label, the messages that carry it are in the inbox. */
        const val INBOX_LABEL_ID: String = "INBOX"
    }
}
