package uk.me.michaellloydlee.recruiter.infrastructure.parser

import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument

/**
 * Extracts the text of a Word document with Apache POI.
 *
 * A document that POI cannot read - a corrupt file or a document in the old
 * binary format - is reported as a failure, the pipeline logs it and moves on
 * to the next attachment.
 */
class DocxTextExtractor : DocumentTextExtractor {
    @Suppress("TooGenericExceptionCaught")
    override fun extract(content: ByteArray): ExtractionResult =
        try {
            XWPFDocument(content.inputStream()).use { document ->
                val text = XWPFWordExtractor(document).getText()
                ExtractionResult.Extracted(TextCleanup.clean(text))
            }
        } catch (error: Exception) {
            // The content is arbitrary bytes, POI can fail in many ways.
            // The contract of the extractor is that it never throws.
            ExtractionResult.Failed("The Word document could not be read: ${error.message}", error)
        }
}
