package uk.me.michaellloydlee.recruiter.infrastructure.parser

/**
 * The outcome of a text extraction.
 */
sealed interface ExtractionResult {
    /**
     * The document was read.
     *
     * @property text the plain text of the document, it is empty when the
     *   document has no text at all (a scan for example).
     */
    data class Extracted(
        val text: String,
    ) : ExtractionResult

    /**
     * The document could not be read.
     *
     * @property reason what went wrong, the pipeline logs it and moves on.
     * @property cause the exception that broke the extraction, if there was one.
     */
    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : ExtractionResult
}
