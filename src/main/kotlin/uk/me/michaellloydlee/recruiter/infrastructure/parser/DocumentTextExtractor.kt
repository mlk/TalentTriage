package uk.me.michaellloydlee.recruiter.infrastructure.parser

/**
 * Extracts the plain text of an attached document.
 */
interface DocumentTextExtractor {
    /**
     * Extracts the plain text of a document.
     *
     * A document that cannot be read is reported as a failure, it never
     * throws: a broken attachment must not stop the pipeline.
     *
     * @param content the bytes of the document.
     */
    fun extract(content: ByteArray): ExtractionResult
}
