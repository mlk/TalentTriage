package uk.me.michaellloydlee.recruiter.infrastructure.browser

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserLauncherTest {
    @Test
    fun macOsOpensTheBrowserWithOpen() {
        assertEquals(listOf("open"), OperatingSystem.MACOS.command)
    }

    @Test
    fun linuxOpensTheBrowserWithXdgOpen() {
        assertEquals(listOf("xdg-open"), OperatingSystem.LINUX.command)
    }

    @Test
    fun windowsOpensTheBrowserWithRundll32() {
        assertEquals(listOf("rundll32", "url.dll,FileProtocolHandler"), OperatingSystem.WINDOWS.command)
    }

    @Test
    fun theSystemOfTheNameIsTheSystemThatRunsTheApplication() {
        assertEquals(OperatingSystem.MACOS, OperatingSystem.of("Mac OS X"))
        assertEquals(OperatingSystem.WINDOWS, OperatingSystem.of("Windows 11"))
        assertEquals(OperatingSystem.LINUX, OperatingSystem.of("Linux"))
    }

    @Test
    fun theUrlIsTheLastArgumentOfTheCommand() {
        var started: List<String> = emptyList()

        val outcome = openInBrowser("https://example.com", listOf("open")) { started = it }

        assertEquals(BrowserOutcome.Opened(listOf("open")), outcome)
        assertEquals(listOf("open", "https://example.com"), started)
    }

    @Test
    fun aBrowserThatCannotBeOpenedIsAFailure() {
        val outcome =
            openInBrowser("https://example.com", listOf("open")) {
                throw IOException("the command is not there")
            }

        assertEquals(BrowserOutcome.Failed("The browser could not be opened: the command is not there"), outcome)
    }

    @Test
    fun theSystemOfTheMachineIsTheOneOfTheNameOfTheJvm() {
        val current = OperatingSystem.current()

        assertEquals(OperatingSystem.of(System.getProperty("os.name").orEmpty()), current)
        assertTrue(current.command.isNotEmpty())
    }
}
