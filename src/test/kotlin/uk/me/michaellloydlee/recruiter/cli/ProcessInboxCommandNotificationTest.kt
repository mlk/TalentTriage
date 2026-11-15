package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.test
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessInboxCommandNotificationTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun callsTheWebhookWithTheMailsThatNeedTheJobSeeker() =
        runTest {
            val sink = StringBuilder()
            val engine = engine()

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --notification-webhook http://webhook.example/hook " +
                            "--credentials ${credentialsFile(root)} " +
                            "--token ${tokenFile(root)} " +
                            "--requirements ${requirementsFile(root)} --rules ${emptyRulesFile(root)}",
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
    fun notifiesAboutAMailThatNeedsMoreInformation() =
        runTest {
            val sink = StringBuilder()
            val engine = engine(officeDays = "unclear")

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --notification-webhook http://webhook.example/hook " +
                            "--credentials ${credentialsFile(root)} " +
                            "--token ${tokenFile(root)} " +
                            "--requirements ${requirementsFile(root)} --rules ${emptyRulesFile(root)}",
                    )

            assertEquals(0, result.statusCode)
            val notification = engine.requestHistory.single { it.url.host == "webhook.example" }
            val body = (notification.body as TextContent).text
            assertTrue(body.contains("\"state\":\"NEEDS_MORE_INFORMATION\""))
        }

    @Test
    fun doesNotNotifyAboutAMailThatIsTrashed() =
        runTest {
            val sink = StringBuilder()
            val engine = engine(jdMatches = "does not match")

            val result =
                cli(sink) { mockClient(engine) }
                    .test(
                        "process-inbox --notification-webhook http://webhook.example/hook " +
                            "--credentials ${credentialsFile(root)} " +
                            "--token ${tokenFile(root)} " +
                            "--requirements ${requirementsFile(root)} --rules ${emptyRulesFile(root)}",
                    )

            assertEquals(0, result.statusCode)
            assertTrue(engine.requestHistory.none { it.url.host == "webhook.example" })
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
                            "--credentials ${credentialsFile(root)} " +
                            "--token ${tokenFile(root)} " +
                            "--requirements ${requirementsFile(root)} --rules ${emptyRulesFile(root)}",
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
                        "process-inbox --credentials ${credentialsFile(root)} " +
                            "--token ${tokenFile(root)} " +
                            "--requirements ${requirementsFile(root)} --rules ${emptyRulesFile(root)}",
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
                        "--credentials ${credentialsFile(root)} " +
                        "--token ${tokenFile(root)} " +
                        "--requirements ${requirementsFile(root)} --rules ${emptyRulesFile(root)}",
                )

            assertEquals(1, result.statusCode)
            assertTrue(result.output.contains("hook.example/notify"))
        }
}
