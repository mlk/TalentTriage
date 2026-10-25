package uk.me.michaellloydlee.recruiter.infrastructure.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tests of the PDF text extractor.
 */
class PdfTextExtractorTest {
    @Test
    fun extractsTheTextOfADocument() {
        val content = TestDocuments.pdfWith(listOf("Hello recruiter", "Your CV was received."))

        val result = PdfTextExtractor().extract(content)

        val text = (result as ExtractionResult.Extracted).text
        assertTrue(text.contains("Hello recruiter"))
        assertTrue(text.contains("Your CV was received."))
    }

    @Test
    fun stripsTheExcessiveWhitespace() {
        val content = TestDocuments.pdfWith(listOf("Hello     recruiter", "  Your CV was received.  "))

        val result = PdfTextExtractor().extract(content)

        val text = (result as ExtractionResult.Extracted).text
        assertTrue(text.contains("Hello recruiter"))
        assertTrue(text.contains("Your CV was received."))
    }

    @Test
    fun reportsACorruptDocumentAsAFailure() {
        val result = PdfTextExtractor().extract(TestDocuments.brokenPdf())

        assertTrue(result is ExtractionResult.Failed)
        assertTrue(result.reason.contains("could not be read"))
    }

    @Test
    fun reportsAnEmptyDocumentAsAFailure() {
        val result = PdfTextExtractor().extract(ByteArray(0))

        assertTrue(result is ExtractionResult.Failed)
    }

    @Test
    fun reportsAPasswordProtectedDocumentAsAFailure() {
        val result = PdfTextExtractor().extract(TestDocuments.protectedPdf())

        assertTrue(result is ExtractionResult.Failed)
        assertTrue(result.reason.contains("password protected"))
    }

    @Test
    fun readsTheTextWithoutSortingByPosition() {
        val content = TestDocuments.pdfWith(listOf("Hello recruiter", "Your CV was received."))

        val result = PdfTextExtractor(sortByPosition = false).extract(content)

        val text = (result as ExtractionResult.Extracted).text
        assertTrue(text.contains("Hello recruiter"))
        assertTrue(text.contains("Your CV was received."))
    }

    @Test
    fun reportsADocumentWithoutTextAsEmpty() {
        val content = TestDocuments.pdfWith(emptyList())

        val result = PdfTextExtractor().extract(content)

        assertEquals("", (result as ExtractionResult.Extracted).text)
    }
}
