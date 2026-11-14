package uk.me.michaellloydlee.recruiter.infrastructure.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesFileTest {
    @Test
    fun anEmptyFileKeepsTheRulesOfTheCode() {
        val rules = RulesFile.load(rulesFile("{}"))

        assertTrue(rules.recruiter.isCandidate(message(subject = "We are looking for a developer")))
        assertTrue(rules.jobListings.isJobListing(message(sender = "jobalerts-noreply@linkedin.com")))
    }

    @Test
    fun aWordOfTheFileMakesACandidate() {
        val rules = RulesFile.load(rulesFile("""{"recruiter_keywords": ["parallelwelt"]}"""))

        assertTrue(rules.recruiter.isCandidate(message(subject = "Deine Parallelwelt wartet")))
        assertFalse(rules.recruiter.isCandidate(message(subject = "We are looking for a developer")))
    }

    @Test
    fun aMissingFieldKeepsTheDefault() {
        val rules = RulesFile.load(rulesFile("""{"recruiter_keywords": ["parallelwelt"]}"""))

        assertTrue(rules.recruiter.isCandidate(message(sender = "jobs at a recruiter")))
        assertTrue(rules.jobListings.isJobListing(message(subject = "new jobs for you")))
    }

    @Test
    fun anEmptyListEmptiesTheRule() {
        val rules = RulesFile.load(rulesFile("""{"recruiter_keywords": [], "recruiter_sender_hints": []}"""))

        assertFalse(rules.recruiter.isCandidate(message(subject = "We are looking for a developer")))
        assertFalse(rules.recruiter.isCandidate(message(sender = "jobs at a recruiter")))
    }

    @Test
    fun theSendersOfTheJobBoardsAreReplaced() {
        val rules = RulesFile.load(rulesFile("""{"job_board_senders": ["s1jobs.example"]}"""))

        assertTrue(rules.jobListings.isJobListing(message(sender = "alerts@s1jobs.example")))
        assertFalse(rules.jobListings.isJobListing(message(sender = "jobalerts-noreply@linkedin.com")))
    }

    @Test
    fun thePhrasesOfTheJobListingsAreReplaced() {
        val rules = RulesFile.load(rulesFile("""{"job_listing_phrases": ["offres d'emploi"]}"""))

        assertTrue(rules.jobListings.isJobListing(message(subject = "Offres d'emploi pour vous")))
        assertFalse(rules.jobListings.isJobListing(message(subject = "new jobs for you")))
    }

    @Test
    fun failsWhenTheFileIsMissing() {
        assertFailsWith<IllegalArgumentException> { RulesFile.load(Path.of("/does/not/exist/rules.json")) }
    }

    @Test
    fun anEmptyFileBehavesLikeTheRulesOfTheCode() {
        val fromFile = RulesFile.load(rulesFile("{}"))
        val defaults = ScreeningRules.defaults()
        val messages =
            listOf(
                message(subject = "We are looking for a developer"),
                message(subject = "Your invoice of April"),
                message(sender = "jobalerts-noreply@linkedin.com"),
                message(subject = "new jobs for you"),
                message(subject = "Hello", sender = "someone@example.com"),
            )

        messages.forEach { message ->
            assertEquals(defaults.recruiter.isCandidate(message), fromFile.recruiter.isCandidate(message))
            assertEquals(defaults.jobListings.isJobListing(message), fromFile.jobListings.isJobListing(message))
        }
    }

    @Test
    fun failsWhenTheFileIsADirectory() {
        val directory = createTempDirectory("rules")

        val error = assertFailsWith<IllegalArgumentException> { RulesFile.load(directory) }

        assertTrue(error.message!!.contains("could not be read"))
    }

    @Test
    fun failsWhenTheFileIsNotValidJson() {
        val file = rulesFile("not json at all")

        val error = assertFailsWith<IllegalArgumentException> { RulesFile.load(file) }

        assertTrue(error.message!!.contains("could not be read"))
    }

    @Test
    fun failsWhenAWordIsBlank() {
        val file = rulesFile("""{"recruiter_keywords": ["  "]}""")

        val error = assertFailsWith<IllegalArgumentException> { RulesFile.load(file) }

        assertTrue(error.message!!.contains("blank"))
    }

    private fun rulesFile(content: String): Path {
        val file = createTempDirectory("rules").resolve("rules.json")
        file.writeText(content)
        return file
    }

    private fun message(
        subject: String = "",
        sender: String = "someone@example.com",
    ): EmailMessage =
        EmailMessage(
            id = "message-id",
            threadId = "thread-id",
            sender = sender,
            subject = subject,
            body = "",
            labelIds = listOf("INBOX"),
        )
}
