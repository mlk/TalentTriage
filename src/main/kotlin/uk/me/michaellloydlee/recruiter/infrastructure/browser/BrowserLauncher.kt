package uk.me.michaellloydlee.recruiter.infrastructure.browser

/**
 * Opens a URL in the browser of the machine.
 *
 * The application is a tool of macOS, the browser is opened with `open`.
 *
 * @param url the URL to open.
 */
fun openInBrowser(url: String) {
    ProcessBuilder("open", url).start()
}
