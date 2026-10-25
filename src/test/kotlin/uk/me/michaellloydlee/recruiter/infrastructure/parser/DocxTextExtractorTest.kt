package uk.me.michaellloydlee.recruiter.infrastructure.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocxTextExtractorTest {
    @Test
    fun extractsTheTextOfADocument() {
        val content = TestWordDocuments.docxWith(listOf("Hello recruiter", "Your CV was received."))

        val result = DocxTextExtractor().extract(content)

        val text = (result as ExtractionResult.Extracted).text
        assertTrue(text.contains("Hello recruiter"))
        assertTrue(text.contains("Your CV was received."))
    }

    @Test
    fun keepsTheParagraphsApart() {
        val content = TestWordDocuments.docxWith(listOf("First paragraph", "Second paragraph"))

        val result = DocxTextExtractor().extract(content)

        val text = (result as ExtractionResult.Extracted).text
        assertTrue(text.contains("First paragraph"))
        assertTrue(text.contains("Second paragraph"))
    }

    @Test
    fun stripsTheExcessiveWhitespace() {
        val content = TestWordDocuments.docxWith(listOf("Hello     recruiter"))

        val result = DocxTextExtractor().extract(content)

        val text = (result as ExtractionResult.Extracted).text
        assertTrue(text.contains("Hello recruiter"))
    }

    @Test
    fun extractsTheCellsOfATable() {
        val content =
            TestWordDocuments.docxWithTable(
                listOf(
                    listOf("Skill", "Years"),
                    listOf("Kotlin", "5"),
                ),
            )

        val result = DocxTextExtractor().extract(content)

        val text = (result as ExtractionResult.Extracted).text
        assertTrue(text.contains("Skill"))
        assertTrue(text.contains("Kotlin"))
        assertTrue(text.contains("5"))
    }

    @Test
    fun leavesTheHeaderAndTheFooterOut() {
        val content =
            TestWordDocuments.docxWithHeaderAndFooter(
                header = "Company confidential",
                footer = "Page 1 of 1",
                body = "Your CV was received.",
            )

        val result = DocxTextExtractor().extract(content)

        val text = (result as ExtractionResult.Extracted).text
        assertTrue(text.contains("Your CV was received."))
        assertTrue(!text.contains("Company confidential"))
        assertTrue(!text.contains("Page 1 of 1"))
    }

    @Test
    fun reportsADocumentWithoutATableAsEmpty() {
        val content = TestWordDocuments.docxWithTable(emptyList())

        val result = DocxTextExtractor().extract(content)

        assertEquals("", (result as ExtractionResult.Extracted).text)
    }

    @Test
    fun reportsACorruptDocumentAsAFailure() {
        val result = DocxTextExtractor().extract(TestWordDocuments.brokenDocx())

        assertTrue(result is ExtractionResult.Failed)
        assertTrue(result.reason.contains("could not be read"))
    }

    @Test
    fun reportsAnEmptyDocumentAsAFailure() {
        val result = DocxTextExtractor().extract(ByteArray(0))

        assertTrue(result is ExtractionResult.Failed)
    }

    @Test
    fun reportsADocumentWithoutTextAsEmpty() {
        val content = TestWordDocuments.docxWith(emptyList())

        val result = DocxTextExtractor().extract(content)

        assertEquals("", (result as ExtractionResult.Extracted).text)
    }
}
