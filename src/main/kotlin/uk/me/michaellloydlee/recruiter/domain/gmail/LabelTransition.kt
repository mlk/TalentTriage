package uk.me.michaellloydlee.recruiter.domain.gmail

/**
 * The labels that are added to and removed from a message when it changes its
 * state.
 *
 * @property add the ids of the labels the message gets.
 * @property remove the ids of the labels the message loses.
 */
data class LabelTransition(
    val add: List<String>,
    val remove: List<String>,
)
