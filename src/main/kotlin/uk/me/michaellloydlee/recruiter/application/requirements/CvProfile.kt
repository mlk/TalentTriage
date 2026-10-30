package uk.me.michaellloydlee.recruiter.application.requirements

/**
 * The profile of the job seeker the model writes from the CV.
 *
 * @property profile what the job descriptions of the recruiters are compared
 *   with, it is written in the words of the job seeker.
 * @property role the job title of the job seeker.
 * @property region the place the job seeker works in.
 */
data class CvProfile(
    val profile: String,
    val role: String,
    val region: String,
)
