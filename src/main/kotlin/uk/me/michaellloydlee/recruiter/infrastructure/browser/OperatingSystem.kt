package uk.me.michaellloydlee.recruiter.infrastructure.browser

/**
 * The system the application runs on.
 *
 * Every system opens the browser with a command of its own.
 *
 * @property command the command that opens a URL in the browser of the
 *   system, the URL is its last argument.
 */
enum class OperatingSystem(
    val command: List<String>,
) {
    /** macOS opens the browser with `open`. */
    MACOS(listOf("open")),

    /** Linux opens the browser with `xdg-open`. */
    LINUX(listOf("xdg-open")),

    /** Windows opens the browser with `rundll32` and the handler of the URLs. */
    WINDOWS(listOf("rundll32", "url.dll,FileProtocolHandler")),
    ;

    companion object {
        /**
         * The system the application runs on.
         *
         * @param name the name of the system, the value of the property
         *   `os.name` of the JVM.
         * @return the system of the name.
         */
        fun of(name: String): OperatingSystem {
            val system = name.lowercase()
            return when {
                system.contains("mac") -> MACOS
                system.startsWith("windows") -> WINDOWS
                else -> LINUX
            }
        }

        /**
         * The system the application runs on.
         */
        fun current(): OperatingSystem = of(System.getProperty("os.name").orEmpty())
    }
}
