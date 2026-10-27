package uk.me.michaellloydlee.recruiter.infrastructure.parser

/**
 * The extractor of a document type.
 *
 * @param mimeType the type of the content, for example `application/pdf`.
 * @return the extractor of the type, `null` when the type is not supported.
 */
fun documentTextExtractor(mimeType: String): DocumentTextExtractor? =
    when (mimeType.lowercase()) {
        PDF_MIME_TYPE -> PDF_EXTRACTOR
        in WORD_MIME_TYPES -> WORD_EXTRACTOR
        else -> null
    }

private val PDF_EXTRACTOR: DocumentTextExtractor = PdfTextExtractor()

private val WORD_EXTRACTOR: DocumentTextExtractor = DocxTextExtractor()

/** The type of a PDF document. */
const val PDF_MIME_TYPE: String = "application/pdf"

/** The types of a Word document. */
val WORD_MIME_TYPES: Set<String> =
    setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-word.document.macroenabled.12",
    )
