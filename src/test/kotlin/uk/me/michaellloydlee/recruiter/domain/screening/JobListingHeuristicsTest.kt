package uk.me.michaellloydlee.recruiter.domain.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JobListingHeuristicsTest {
    @Test
    fun theAlertOfAJobBoardIsAListing() {
        val message = message(sender = "LinkedIn Job Alerts <jobalerts-noreply@linkedin.com>")

        assertTrue(heuristics.isJobListing(message))
    }

    @Test
    fun theMailOfAPersonOfTheSameBoardIsNoListing() {
        val message =
            message(
                subject = "JUXT - Forward Deployed Engineer - London",
                sender = "Andrew Clark <inmail-hit-reply@linkedin.com>",
            )

        assertFalse(heuristics.isJobListing(message))
    }

    @Test
    fun aPhraseOfTheSubjectMakesItAListing() {
        val message = message(subject = "5 new jobs for you")

        assertTrue(heuristics.isJobListing(message))
    }

    @Test
    fun aPhraseOfTheBodyMakesItAListing() {
        val message = message(subject = "Hello", body = "Here are the jobs matching your profile")

        assertTrue(heuristics.isJobListing(message))
    }

    @Test
    fun thePhrasesAreMatchedWithoutTheCase() {
        val message = message(subject = "JOBS FOR YOU")

        assertTrue(heuristics.isJobListing(message))
    }

    @Test
    fun aRecruiterMailIsNoListing() {
        val message =
            message(
                subject = "Your CV",
                body = "We are looking for a developer in Berlin, your profile fits the job",
                sender = "recruiter@talent-partner.com",
            )

        assertFalse(heuristics.isJobListing(message))
    }

    @Test
    fun theRulesCanBeReplaced() {
        val own = JobListingHeuristics(senderHints = emptySet(), phrases = setOf("invoice"))
        val message = message(subject = "Your invoice")

        assertTrue(own.isJobListing(message))
        assertFalse(heuristics.isJobListing(message))
    }

    @Test
    fun withoutAnyRuleNoMessageIsAListing() {
        val own = JobListingHeuristics(senderHints = emptySet(), phrases = emptySet())
        val message = message(subject = "5 new jobs for you", sender = "LinkedIn Job Alerts <jobalerts@linkedin.com>")

        assertFalse(own.isJobListing(message))
    }

    private val heuristics = JobListingHeuristics()

    private fun message(
        subject: String = "",
        body: String = "",
        sender: String = "someone@example.com",
    ): EmailMessage =
        EmailMessage(
            id = "message-id",
            threadId = "thread-id",
            sender = sender,
            subject = subject,
            body = body,
            labelIds = listOf("INBOX"),
        )
}
