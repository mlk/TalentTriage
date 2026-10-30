package uk.me.michaellloydlee.recruiter.cli

import org.w3c.dom.Element
import org.w3c.dom.Node
import uk.me.michaellloydlee.recruiter.domain.gmail.messageWindow
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The launchd configuration of macOS is a sample of the repository.
 *
 * The test reads it from the class path, so it does not depend on the working
 * directory of the build.
 */
class LaunchdConfigurationTest {
    @Test
    fun hasTheLabelOfTheJob() {
        assertEquals("com.user.recruiter-checker", text("Label"))
    }

    @Test
    fun runsTheJarWithTheProcessInboxCommand() {
        val arguments = arguments()

        assertEquals("/usr/bin/java", arguments.first())
        assertTrue(arguments.contains("-jar"))
        assertTrue(arguments.any { it.endsWith(".jar") })
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
    fun passesAWindowOfTheRunToTheCommand() {
        val window = messageWindow(argumentAfter(arguments(), "--since")).duration

        // The window has to cover the gap between two runs, the job runs every hour.
        assertTrue(window >= Duration.ofHours(1))
    }

    @Test
    fun redirectsTheStandardOutputAndTheStandardError() {
        val out = text("StandardOutPath")
        val err = text("StandardErrorPath")

        assertTrue(out.endsWith(".log"))
        assertTrue(err.endsWith(".log"))
        assertTrue(out != err)
    }

    @Test
    fun runsTheJobEveryHour() {
        val interval = requireNotNull(value("StartCalendarInterval"))

        assertEquals("0", text(interval, "Minute"))
        // An hour in the interval pins the job to that hour of the day.
        assertNull(value(interval, "Hour"))
    }

    private fun arguments(): List<String> = strings(requireNotNull(value("ProgramArguments")))

    private fun argumentAfter(
        arguments: List<String>,
        option: String,
    ): String = arguments[arguments.indexOf(option) + 1]

    private fun text(key: String): String = strings(requireNotNull(value(key))).single()

    private fun text(
        dictionary: Element,
        key: String,
    ): String = strings(requireNotNull(value(dictionary, key))).single()

    private fun value(key: String): Element? = value(dictionary(), key)

    private fun value(
        dictionary: Element,
        key: String,
    ): Element? {
        val children = dictionary.childNodes
        var current: String? = null
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node !is Element) {
                continue
            }
            if (node.tagName == "key") {
                current = node.textContent.trim()
            } else if (current == key) {
                return node
            }
        }
        return null
    }

    private fun strings(element: Element): List<String> =
        when (element.tagName) {
            "array" -> {
                (0 until element.childNodes.length).mapNotNull { index ->
                    val child = element.childNodes.item(index)
                    if (child.nodeType == Node.ELEMENT_NODE) child.textContent.trim() else null
                }
            }

            else -> {
                listOf(element.textContent.trim())
            }
        }

    private fun dictionary(): Element = requireNotNull(document().getElementsByTagName("dict").item(0) as? Element)

    private fun document() =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                // The plist names the DTD of Apple, the parser must not fetch it.
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }.newDocumentBuilder()
            .parse(
                requireNotNull(
                    LaunchdConfigurationTest::class.java.getResourceAsStream("/$PLIST_RESOURCE"),
                ),
            )

    companion object {
        /** The name of the sample of the launchd configuration. */
        private const val PLIST_RESOURCE = "com.user.recruiter-checker.plist"
    }
}
