package uk.me.michaellloydlee.recruiter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The systemd configuration of Linux is a sample of the repository.
 *
 * The test reads it from the class path, so it does not depend on the working
 * directory of the build.
 */
class SystemdConfigurationTest {
    @Test
    fun runsTheJarWithTheProcessInboxCommand() {
        val arguments = argumentsOf(service, "ExecStart")

        assertTrue(arguments.contains("-jar"))
        assertTrue(arguments.any { it.endsWith(".jar") })
        assertTrue(arguments.contains("process-inbox"))
    }

    @Test
    fun silencesTheWarningOfTheTerminalDetection() {
        assertTrue(argumentsOf(service, "ExecStart").contains("--enable-native-access=ALL-UNNAMED"))
    }

    @Test
    fun passesTheFilesOfTheRunToTheCommand() {
        val arguments = argumentsOf(service, "ExecStart")

        assertTrue(argumentAfter(arguments, "--credentials").endsWith("credentials.json"))
        assertTrue(argumentAfter(arguments, "--token").endsWith("token.json"))
        assertTrue(argumentAfter(arguments, "--requirements").endsWith("requirements.json"))
    }

    @Test
    fun leavesTheWindowOfTheRunToTheRun() {
        // The run looks back to the moment of the last successful run, a
        // window in the sample would fix it to a length that is wrong for
        // every machine but one.
        assertTrue(!argumentsOf(service, "ExecStart").contains("--since"))
    }

    @Test
    fun runsInTheDirectoryOfTheInstallation() {
        assertTrue(service.getValue("WorkingDirectory").isNotEmpty())
    }

    @Test
    fun writesTheLogOfTheRunToTheFilesOfTheInstallation() {
        assertTrue(service.getValue("StandardOutput").endsWith(".log"))
        assertTrue(service.getValue("StandardError").endsWith(".log"))
    }

    @Test
    fun theTimerRunsTheServiceEveryHour() {
        assertEquals("hourly", timer.getValue("OnCalendar"))
        assertEquals("recruiter-cli.service", timer.getValue("Unit"))
    }

    @Test
    fun theTimerIsEnabled() {
        assertEquals("timers.target", timer.getValue("WantedBy"))
    }

    private val service: Map<String, String> by lazy { unit(SERVICE_RESOURCE) }

    private val timer: Map<String, String> by lazy { unit(TIMER_RESOURCE) }

    private fun argumentsOf(
        unit: Map<String, String>,
        key: String,
    ): List<String> = unit.getValue(key).split(" ").filter { it.isNotEmpty() }

    private fun argumentAfter(
        arguments: List<String>,
        option: String,
    ): String = arguments[arguments.indexOf(option) + 1]

    private fun unit(resource: String): Map<String, String> =
        requireNotNull(SystemdConfigurationTest::class.java.getResourceAsStream("/$resource"))
            .readBytes()
            .decodeToString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("[") }
            .associate { line -> line.substringBefore("=") to line.substringAfter("=") }

    companion object {
        /** The name of the sample of the systemd service. */
        private const val SERVICE_RESOURCE = "recruiter-cli.service"

        /** The name of the sample of the systemd timer. */
        private const val TIMER_RESOURCE = "recruiter-cli.timer"
    }
}
