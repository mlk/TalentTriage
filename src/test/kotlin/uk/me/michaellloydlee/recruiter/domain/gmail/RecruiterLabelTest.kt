package uk.me.michaellloydlee.recruiter.domain.gmail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecruiterLabelTest {
    @Test
    fun theLabelsAreNestedBelowTheRecruitersPrefix() {
        assertTrue(RecruiterLabel.entries.all { it.path.startsWith("Recruiters/") })
        assertEquals(
            listOf(
                "Recruiters/FilteredOut",
                "Recruiters/JobListing",
                "Recruiters/WorthFollowUp",
                "Recruiters/NeedsMoreInformation",
                "Recruiters/Trash",
            ),
            RecruiterLabel.entries.map { it.path },
        )
    }

    @Test
    fun onlyTheTrashLabelArchivesTheMessage() {
        assertTrue(RecruiterLabel.TRASH.archives)
        assertFalse(RecruiterLabel.FILTERED_OUT.archives)
        assertFalse(RecruiterLabel.JOB_LISTING.archives)
        assertFalse(RecruiterLabel.WORTH_FOLLOW_UP.archives)
        assertFalse(RecruiterLabel.NEEDS_MORE_INFORMATION.archives)
    }

    @Test
    fun theSearchMatchesTheInboxMessagesWithoutAStateLabel() {
        assertEquals(
            "is:inbox " +
                "-label:Recruiters/FilteredOut " +
                "-label:Recruiters/JobListing " +
                "-label:Recruiters/WorthFollowUp " +
                "-label:Recruiters/NeedsMoreInformation " +
                "-label:Recruiters/Trash",
            RecruiterLabel.UNPROCESSED_QUERY,
        )
    }

    @Test
    fun theSearchExcludesEveryStateLabelByItsPath() {
        assertTrue(
            RecruiterLabel.entries.all { "-label:${it.path}" in RecruiterLabel.UNPROCESSED_QUERY },
        )
    }

    @Test
    fun theSearchHasNoWildcardBecauseGmailDoesNotUnderstandOne() {
        assertFalse(
            RecruiterLabel.UNPROCESSED_QUERY.contains("*"),
            "Gmail ignores a wildcard in the name of a label",
        )
    }

    @Test
    fun aTransitionAddsTheNewStateAndRemovesTheOtherStates() {
        val ids =
            mapOf(
                RecruiterLabel.FILTERED_OUT to "id-filtered",
                RecruiterLabel.JOB_LISTING to "id-listing",
                RecruiterLabel.WORTH_FOLLOW_UP to "id-worth",
                RecruiterLabel.NEEDS_MORE_INFORMATION to "id-more",
                RecruiterLabel.TRASH to "id-trash",
            )

        val transition = RecruiterLabel.WORTH_FOLLOW_UP.transition(ids, inboxLabelId = "INBOX")

        assertEquals(listOf("id-worth"), transition.add)
        assertEquals(listOf("id-filtered", "id-listing", "id-more", "id-trash"), transition.remove)
    }

    @Test
    fun aTransitionThatArchivesRemovesTheInboxLabel() {
        val ids = RecruiterLabel.entries.associateWith { "id-${it.name}" }

        val transition = RecruiterLabel.TRASH.transition(ids, inboxLabelId = "INBOX")

        assertEquals(listOf("id-TRASH"), transition.add)
        assertTrue(transition.remove.contains("INBOX"))
    }

    @Test
    fun aTransitionWithoutKnownLabelIdsOnlyRemovesTheInboxLabel() {
        val transition = RecruiterLabel.TRASH.transition(emptyMap(), inboxLabelId = "INBOX")

        assertEquals(emptyList(), transition.add)
        assertEquals(listOf("INBOX"), transition.remove)
    }
}
