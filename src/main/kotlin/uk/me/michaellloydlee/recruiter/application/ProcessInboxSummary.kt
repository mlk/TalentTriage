package uk.me.michaellloydlee.recruiter.application

import uk.me.michaellloydlee.recruiter.domain.gmail.MessageWindow

/**
 * What a run of the pipeline did.
 *
 * @property screened how many messages were looked at.
 * @property recruiters how many of them are recruiter mails.
 * @property filteredOut how many of them were not recruiter mails. They are
 *   reported, they are not labelled.
 * @property jobListings how many of them were job listings, a listing is not
 *   one job and is not evaluated.
 * @property unknown how many of them the screening could not decide, they stay
 *   in the inbox without a state label.
 * @property worthFollowUp how many of them are worth a follow up.
 * @property needsMoreInformation how many of them the job seeker has to look
 *   at: they match but do not say everything the job seeker needs to know, or
 *   the model could not evaluate them.
 * @property trashed how many of them do not match at all.
 * @property window how far back the run looked for messages.
 */
data class ProcessInboxSummary(
    val screened: Int,
    val recruiters: Int,
    val filteredOut: Int,
    val unknown: Int,
    val jobListings: Int = 0,
    val worthFollowUp: Int = 0,
    val needsMoreInformation: Int = 0,
    val trashed: Int = 0,
    val window: MessageWindow = MessageWindow.LAST_DAY,
)
