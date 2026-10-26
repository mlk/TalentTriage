package uk.me.michaellloydlee.recruiter.application

/**
 * What a run of the pipeline did.
 *
 * @property screened how many messages were looked at.
 * @property recruiters how many of them are recruiter mails.
 * @property filteredOut how many of them were filtered out.
 * @property unknown how many of them the screening could not decide.
 */
data class ProcessInboxSummary(
    val screened: Int,
    val recruiters: Int,
    val filteredOut: Int,
    val unknown: Int,
)
