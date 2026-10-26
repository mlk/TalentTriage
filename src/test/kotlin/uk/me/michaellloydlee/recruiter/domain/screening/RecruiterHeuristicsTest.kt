package uk.me.michaellloydlee.recruiter.domain.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecruiterHeuristicsTest {
    @Test
    fun aKeywordInTheSubjectMakesItACandidate() {
        val message = message(subject = "Your application for the Kotlin position")

        assertTrue(heuristics.isCandidate(message))
    }

    @Test
    fun aKeywordInTheBodyMakesItACandidate() {
        val message = message(subject = "Hello", body = "We are looking for a developer in Berlin")

        assertTrue(heuristics.isCandidate(message))
    }

    @Test
    fun theWordsAreMatchedWithoutTheCase() {
        val message = message(subject = "INTERVIEW INVITATION")

        assertTrue(heuristics.isCandidate(message))
    }

    @Test
    fun aSenderOfARecruiterMakesItACandidate() {
        val message = message(subject = "Hello", sender = "jobs at talent-partner dot com")

        assertTrue(heuristics.isCandidate(message))
    }

    @Test
    fun aMessageWithoutAKeywordIsNoCandidate() {
        val message = message(subject = "Your invoice", body = "The invoice of March is attached")

        assertFalse(heuristics.isCandidate(message))
    }

    @Test
    fun theRulesCanBeReplaced() {
        val message = message(subject = "Your invoice")
        val own = RecruiterHeuristics(keywords = setOf("invoice"), senderHints = emptySet())

        assertTrue(own.isCandidate(message))
        assertFalse(heuristics.isCandidate(message))
    }

    private val heuristics = RecruiterHeuristics()

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
