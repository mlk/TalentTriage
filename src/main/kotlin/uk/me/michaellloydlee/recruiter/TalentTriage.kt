package uk.me.michaellloydlee.recruiter

/**
 * Metadata describing the TalentTriage application.
 *
 * The values exposed here are consumed by the CLI entry point and by the
 * `--version` style output, so they are kept free of any runtime dependency.
 */
object TalentTriage {
    /** Human readable name of the project. */
    const val NAME: String = "TalentTriage"

    /** Root package that contains every production source of the application. */
    const val ROOT_PACKAGE: String = "uk.me.michaellloydlee.recruiter"

    /**
     * Renders a short, human readable description of the application.
     *
     * @return a single line description such as `TalentTriage (uk.me.michaellloydlee.recruiter)`.
     */
    fun describe(): String = "$NAME ($ROOT_PACKAGE)"
}
