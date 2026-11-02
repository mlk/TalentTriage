package uk.me.michaellloydlee.recruiter.infrastructure.browser

import java.io.IOException

/**
 * Opens a URL in the browser of the machine.
 *
 * The command of the system opens the browser: `open` on macOS, `xdg-open`
 * on Linux and `rundll32` on Windows. A browser that cannot be opened is not
 * a failure: the URL is printed and the job seeker opens it by hand.
 *
 * The command is not waited for: the browser opens on its own and its exit
 * code says nothing about the authorisation. Only the failure of the command
 * itself - a machine without `xdg-open`, for example - is a failure.
 *
 * @param url the URL to open.
 * @param command the command that opens the browser, the URL is its last
 *   argument.
 * @param start starts the command, it is a parameter of its own so that a
 *   test does not open a browser.
 * @return what the opening did.
 */
fun openInBrowser(
    url: String,
    command: List<String> = OperatingSystem.current().command,
    start: (List<String>) -> Unit = { ProcessBuilder(it).start() },
): BrowserOutcome =
    try {
        start(command + url)
        BrowserOutcome.Opened(command)
    } catch (error: IOException) {
        BrowserOutcome.Failed("The browser could not be opened: ${error.message}")
    }
