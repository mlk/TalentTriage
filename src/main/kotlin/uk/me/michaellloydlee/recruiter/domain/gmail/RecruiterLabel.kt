package uk.me.michaellloydlee.recruiter.domain.gmail

private const val LABEL_PREFIX = "Recruiters/"

/**
 * The state labels the triage pipeline applies to the messages.
 *
 * Gmail is the state store of the pipeline: a message that carries one of
 * these labels was processed already and is not looked at again. The labels
 * are nested below the `Recruiters/` prefix, so the whole family can be
 * excluded from a search with one `-label:…` per state.
 *
 * @property path the full path of the label in Gmail.
 * @property archives whether a message with this label is archived.
 */
enum class RecruiterLabel(
    val path: String,
    val archives: Boolean,
) {
    /**
     * The message is a job listing - an alert of a job board - and not one
     * job, it stays in the inbox.
     */
    JOB_LISTING("${LABEL_PREFIX}JobListing", archives = false),

    /** The message is worth a follow up, it stays in the inbox. */
    WORTH_FOLLOW_UP("${LABEL_PREFIX}WorthFollowUp", archives = false),

    /**
     * The message is a recruiter mail that matches, but something is missing
     * for the decision: the pay is not stated or the job description does not
     * say how many days of office it demands. It stays in the inbox, the job
     * seeker looks at it.
     */
    NEEDS_MORE_INFORMATION("${LABEL_PREFIX}NeedsMoreInformation", archives = false),

    /** The message is not worth any attention, it is archived. */
    TRASH("${LABEL_PREFIX}Trash", archives = true),
    ;

    companion object {
        /** The prefix all state labels share. */
        const val PREFIX: String = LABEL_PREFIX

        /**
         * The search that matches the inbox messages without a state label.
         *
         * Gmail does not understand a wildcard in the name of a label: the
         * search `is:inbox -label:Recruiters/…` still returns the messages
         * that carry a nested label, and the parent label `Recruiters` does
         * not cover the nested ones either. Every state label is excluded by
         * its own path, that is the only search that works.
         */
        val UNPROCESSED_QUERY: String =
            "is:inbox " + entries.joinToString(separator = " ") { "-label:${it.path}" }
    }
}

/**
 * Builds the label transition of a message that changes its state.
 *
 * A message carries exactly one state label: the other state labels are
 * removed, so a message that was filtered out and is worth a follow up
 * afterwards does not keep both labels. A message whose new state archives it
 * loses the inbox label as well, that is how Gmail archives a message.
 *
 * @param stateLabelIds the ids of the state labels, missing ones are ignored.
 * @param inboxLabelId the id of the inbox label.
 * @return the labels to add and to remove.
 */
fun RecruiterLabel.transition(
    stateLabelIds: Map<RecruiterLabel, String>,
    inboxLabelId: String,
): LabelTransition {
    val add = listOfNotNull(stateLabelIds[this])
    val remove =
        RecruiterLabel.entries
            .filter { it != this }
            .mapNotNull { stateLabelIds[it] }
            .toMutableList()
    if (archives) {
        remove.add(inboxLabelId)
    }
    return LabelTransition(add = add, remove = remove)
}
