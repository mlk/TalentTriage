package uk.me.michaellloydlee.recruiter.infrastructure.parser

/**
 * Removes the noise from the text of a document.
 *
 * The text of a document is meant to be read by a language model, so the
 * layout of the page - the spaces between the words, the empty lines, the
 * control characters of the format - is noise that only costs tokens.
 */
object TextCleanup {
    /**
     * Cleans the text of a document.
     *
     * @param text the raw text of the document.
     * @return the text without the control characters, with single spaces
     *   between the words and without the empty lines.
     */
    fun clean(text: String): String =
        text
            .replace(WINDOWS_LINE_ENDING, "\n")
            .replace('\r', '\n')
            .replace('\t', ' ')
            .filter {
                it == '\n' || it == ' ' || (!it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt())
            }.replace(HORIZONTAL_WHITESPACE, " ")
            .lines()
            .joinToString(separator = "\n") { it.trim() }
            .replace(EMPTY_LINES, "\n\n")
            .trim()

    private const val WINDOWS_LINE_ENDING = "\r\n"
    private val HORIZONTAL_WHITESPACE = Regex(" +")
    private val EMPTY_LINES = Regex("\n{3,}")
}
