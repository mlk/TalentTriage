package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.test
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessInboxCommandNotificationTest {
    @AfterEach
    fun cleanup() {
        cleanupTempDirs()
    }

    @Test
    fun callsTheWebhookWithTheMailsThatNeedTheJobSeeker() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --notification-webhook http://webhook.example/hook " +
                            "--credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(0, result.statusCode)
            val notification = engine.requestHistory.single { it.url.host == "webhook.example" }
            assertEquals("POST", notification.method.value)
            val body = (notification.body as TextContent).text
            assertTrue(body.contains("\"id\":\"message-id\""))
            assertTrue(body.contains("\"sender\":\"recruiter@example.com\""))
            assertTrue(body.contains("\"state\":\"WORTH_FOLLOW_UP\""))
            assertTrue(records(sink).any { it.event == "process-inbox.notification_sent" })
        }

    @Test
    fun keepsTheRunGoingWhenTheWebhookAnswersWithAnError() =
        runTest {
            val sink = StringBuilder()
            val engine = engine(webhookStatus = HttpStatusCode.InternalServerError)

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --notification-webhook http://webhook.example/hook " +
                            "--credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(0, result.statusCode)
            assertEquals("process-inbox.completed", records(sink).last().event)
            assertTrue(records(sink).any { it.event == "process-inbox.notification_failed" })
        }

    @Test
    fun sendsNoNotificationWithoutTheWebhookOption() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --credentials ${credentialsFile()} " +
                            "--token ${tokenFile()} " +
                            "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                    )

            assertEquals(0, result.statusCode)
            assertTrue(engine.requestHistory.none { it.url.host == "webhook.example" })
            assertTrue(records(sink).none { it.event == "process-inbox.notification_sent" })
        }

    @Test
    fun failsOnAWebhookWithoutAScheme() =
        runTest {
            val sink = StringBuilder()

            val result =
                cli(sink).test(
                    "process-inbox --notification-webhook hook.example/notify " +
                        "--credentials ${credentialsFile()} " +
                        "--token ${tokenFile()} " +
                        "--requirements ${requirementsFile()} --rules ${emptyRulesFile()}",
                )

            assertEquals(1, result.statusCode)
            assertTrue(result.output.contains("hook.example/notify"))
        }
}
