package uk.me.michaellloydlee.recruiter.infrastructure.parser

import org.apache.poi.EncryptedDocumentException
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable

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
                ExtractionResult.Extracted(TextCleanup.clean(bodyText(document)))
            }
        } catch (error: EncryptedDocumentException) {
            ExtractionResult.Failed("The Word document is password protected and could not be read", error)
        } catch (error: Exception) {
            // The content is arbitrary bytes, POI can fail in many ways.
            // The contract of the extractor is that it never throws.
            ExtractionResult.Failed("The Word document could not be read: ${error.message}", error)
        }

    /**
     * The text of the body of the document.
     *
     * The headers and the footers are left out: they are the template of the
     * document, not its content, and they would only cost tokens in the
     * prompt.
     */
    private fun bodyText(document: XWPFDocument): String =
        document.bodyElements.joinToString(separator = "\n") { element ->
            when (element) {
                is XWPFParagraph -> element.text
                is XWPFTable -> element.text
                else -> ""
            }
        }
}
