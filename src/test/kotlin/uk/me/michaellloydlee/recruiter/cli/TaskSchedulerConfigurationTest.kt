package uk.me.michaellloydlee.recruiter.cli

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The task of the Task Scheduler of Windows is a sample of the repository.
 *
 * The test reads it from the class path, so it does not depend on the working
 * directory of the build.
 */
class TaskSchedulerConfigurationTest {
    @Test
    fun runsTheJarWithTheProcessInboxCommand() {
        val arguments = arguments()

        assertTrue(arguments.contains("-jar"))
        assertTrue(arguments.any { it.trim('"').endsWith(".jar") })
        assertTrue(arguments.contains("process-inbox"))
    }

    @Test
    fun silencesTheWarningOfTheTerminalDetection() {
        assertTrue(arguments().contains("--enable-native-access=ALL-UNNAMED"))
    }

    @Test
    fun passesTheFilesOfTheRunToTheCommand() {
        val arguments = arguments()

        assertTrue(argumentAfter(arguments, "--credentials").endsWith("credentials.json"))
        assertTrue(argumentAfter(arguments, "--token").endsWith("token.json"))
        assertTrue(argumentAfter(arguments, "--requirements").endsWith("requirements.json"))
    }

    @Test
    fun leavesTheWindowOfTheRunToTheRun() {
        // The run looks back to the moment of the last successful run, a
        // window in the sample would fix it to a length that is wrong for
        // every machine but one.
        assertTrue(!arguments().contains("--since"))
    }

    @Test
    fun runsTheJavaOfTheMachine() {
        assertTrue(command().endsWith("java.exe"))
    }

    @Test
    fun runsInTheDirectoryOfTheInstallation() {
        assertTrue(text("WorkingDirectory").isNotEmpty())
    }

    @Test
    fun runsTheTaskEveryHour() {
        assertEquals("PT1H", text("Interval"))
        assertEquals("1", text("DaysInterval"))
    }

    @Test
    fun runsTheTaskOnALaptopOnBattery() {
        // The job of macOS runs on a laptop on battery, a triage pass that is
        // missed because the machine is mobile is a mail that is looked at an
        // hour later.
        assertEquals("false", text("DisallowStartIfOnBatteries"))
        assertEquals("false", text("StopIfGoingOnBatteries"))
    }

    private fun arguments(): List<String> = text("Arguments").split(" ").filter { it.isNotEmpty() }

    private fun argumentAfter(
        arguments: List<String>,
        option: String,
    ): String = arguments[arguments.indexOf(option) + 1].trim('"')

    private fun command(): String = text("Command")

    private fun text(tag: String): String =
        document()
            .getElementsByTagName(tag)
            .let { nodes ->
                requireNotNull(nodes.item(0) as? Element) { "The task has no $tag" }
            }.textContent
            .trim()

    private fun document() =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                // The task of the Task Scheduler has a namespace of its own,
                // the test looks at the names of the elements.
                isNamespaceAware = false
            }.newDocumentBuilder()
            .parse(
                requireNotNull(
                    TaskSchedulerConfigurationTest::class.java.getResourceAsStream("/$TASK_RESOURCE"),
                ),
            )

    companion object {
        /** The name of the sample of the task of the Task Scheduler. */
        private const val TASK_RESOURCE = "recruiter-cli.xml"
    }
}
