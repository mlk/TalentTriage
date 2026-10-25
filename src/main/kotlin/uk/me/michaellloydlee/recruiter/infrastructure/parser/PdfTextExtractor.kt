package uk.me.michaellloydlee.recruiter.infrastructure.parser

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.IOException

/**
 * Extracts the text of a PDF document with Apache PDFBox.
 *
 * A document that PDFBox cannot read - a corrupt or an encrypted file - is
 * reported as a failure, the pipeline logs it and moves on to the next
 * attachment.
 *
 * @param sortByPosition whether the words are read in the order they appear
 *   on the page instead of the order the content stream has them.
 */
class PdfTextExtractor(
    private val sortByPosition: Boolean = true,
) : DocumentTextExtractor {
    override fun extract(content: ByteArray): ExtractionResult =
        try {
            Loader.loadPDF(content).use { document ->
                val text =
                    PDFTextStripper()
                        .apply { this.sortByPosition = this@PdfTextExtractor.sortByPosition }
                        .getText(document)
                ExtractionResult.Extracted(TextCleanup.clean(text))
            }
        } catch (error: IOException) {
            ExtractionResult.Failed("The PDF could not be read: ${error.message}")
        }
}
