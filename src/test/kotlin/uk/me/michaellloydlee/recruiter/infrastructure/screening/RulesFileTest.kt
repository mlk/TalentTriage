package uk.me.michaellloydlee.recruiter.infrastructure.screening

import org.junit.jupiter.api.AfterEach
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
    private val tempDirs = mutableListOf<Path>()

    private fun tempDir(prefix: String): Path = createTempDirectory(prefix).also { tempDirs.add(it) }

    @AfterEach
    fun cleanupTempDirs() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
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
    fun anEmptyFileEqualsTheRulesOfTheCode() {
        assertEquals(ScreeningRules.defaults(), RulesFile.load(rulesFile("{}")))
    }

    @Test
    fun failsWhenTheFileIsADirectory() {
        val directory = tempDir("rules")

        val error = assertFailsWith<IllegalArgumentException> { RulesFile.load(directory) }

        assertTrue(error.message!!.contains("could not be read"))
    }

    @Test
    fun failsWhenTheFileIsEmpty() {
        val file = rulesFile("")

        val error = assertFailsWith<IllegalArgumentException> { RulesFile.load(file) }

        assertTrue(error.message!!.contains("is empty"))
    }

    @Test
    fun aWordWithSurroundingSpacesMatchesAfterTrimming() {
        val rules = RulesFile.load(rulesFile("""{"recruiter_keywords": ["  engineer  "]}"""))

        assertTrue(rules.recruiter.isCandidate(message(subject = "We are looking for an ENGINEER")))
    }

    @Test
    fun aWordWithRegexMetacharactersMatchesLiterally() {
        val rules = RulesFile.load(rulesFile("""{"recruiter_keywords": ["c++", "c#", ".net"]}"""))

        assertTrue(rules.recruiter.isCandidate(message(subject = "Senior C++ engineer needed")))
        assertTrue(rules.recruiter.isCandidate(message(subject = "C# developer")))
        assertTrue(rules.recruiter.isCandidate(message(subject = "ASP.NET Core team")))
        assertFalse(rules.recruiter.isCandidate(message(subject = "Senior Cxx engineer needed")))
    }

    @Test
    fun failsWhenTheFileHoldsAnUnknownKey() {
        val file = rulesFile("""{"recruiter_keyword": ["stellenangebot"]}""")

        val error = assertFailsWith<IllegalArgumentException> { RulesFile.load(file) }

        assertTrue(error.message!!.contains("recruiter_keyword"))
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
        val file = tempDir("rules").resolve("rules.json")
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
