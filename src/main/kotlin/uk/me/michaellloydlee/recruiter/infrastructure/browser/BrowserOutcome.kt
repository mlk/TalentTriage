package uk.me.michaellloydlee.recruiter.infrastructure.browser

/**
 * What the opening of the browser did.
 */
sealed interface BrowserOutcome {
    /**
     * The browser was opened.
     *
     * @property command the command that opened the browser.
     */
    data class Opened(
        val command: List<String>,
    ) : BrowserOutcome

    /**
     * The browser was not opened.
     *
     * @property reason why the browser was not opened, it goes into the log.
     */
    data class Failed(
        val reason: String,
    ) : BrowserOutcome
}
