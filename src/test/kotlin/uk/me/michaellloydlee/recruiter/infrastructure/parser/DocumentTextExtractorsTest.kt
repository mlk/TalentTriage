package uk.me.michaellloydlee.recruiter.infrastructure.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentTextExtractorsTest {
    @Test
    fun findsTheExtractorOfAPdf() {
        assertEquals(PdfTextExtractor::class, documentTextExtractorFor("cv.pdf")?.let { it::class })
    }

    @Test
    fun findsTheExtractorOfAWordDocument() {
        assertEquals(DocxTextExtractor::class, documentTextExtractorFor("cv.docx")?.let { it::class })
    }

    @Test
    fun findsTheExtractorOfAFileWithALongName() {
        assertEquals(PdfTextExtractor::class, documentTextExtractorFor("my-cv-2026.PDF")?.let { it::class })
    }

    @Test
    fun hasNoExtractorForAnUnknownType() {
        assertNull(documentTextExtractorFor("cv.txt"))
    }

    @Test
    fun hasNoExtractorForAFileWithoutAnExtension() {
        assertNull(documentTextExtractorFor("cv"))
    }
}
