package uk.me.michaellloydlee.recruiter.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue

class RecruiterCliTest {
    @Test
    fun mainRunsTheProcessInboxCommand() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out

        System.setOut(PrintStream(output))
        try {
            main(arrayOf(ProcessInboxCommand.COMMAND_NAME))
        } finally {
            System.setOut(originalOut)
        }

        assertTrue(output.toString().contains("process-inbox.started"))
        assertTrue(output.toString().contains("process-inbox.completed"))
    }
}
