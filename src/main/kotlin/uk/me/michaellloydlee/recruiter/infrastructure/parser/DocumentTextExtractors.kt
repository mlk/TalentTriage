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

/**
 * The extractor of a document file.
 *
 * The documents of the mailbox carry their type, a document on the disk only
 * carries its name.
 *
 * @param fileName the name of the file, for example `cv.pdf`.
 * @return the extractor of the type of the file, `null` when the type is not
 *   supported.
 */
fun documentTextExtractorFor(fileName: String): DocumentTextExtractor? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        PDF_EXTENSION -> documentTextExtractor(PDF_MIME_TYPE)
        WORD_EXTENSION -> documentTextExtractor(WORD_MIME_TYPE)
        else -> null
    }

private val PDF_EXTRACTOR: DocumentTextExtractor = PdfTextExtractor()

private val WORD_EXTRACTOR: DocumentTextExtractor = DocxTextExtractor()

/** The type of a PDF document. */
const val PDF_MIME_TYPE: String = "application/pdf"

/** The type of a Word document. */
const val WORD_MIME_TYPE: String = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** The types of a Word document. */
val WORD_MIME_TYPES: Set<String> =
    setOf(
        WORD_MIME_TYPE,
        "application/vnd.ms-word.document.macroenabled.12",
    )

/** The extension of a PDF document. */
const val PDF_EXTENSION: String = "pdf"

/** The extension of a Word document. */
const val WORD_EXTENSION: String = "docx"
