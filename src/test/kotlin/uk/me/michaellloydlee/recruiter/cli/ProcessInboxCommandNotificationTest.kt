package uk.me.michaellloydlee.recruiter.cli

import com.github.ajalt.clikt.command.test
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
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
            val engine = engine(messageList = MESSAGE_LIST_TWO)

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
            val body =
                Json.parseToJsonElement((notification.body as TextContent).text).jsonObject
            val mails = body["mails"]!!.jsonArray
            assertEquals(
                listOf("first", "second"),
                mails.map {
                    it.jsonObject
                        .getValue("id")
                        .jsonPrimitive.content
                },
            )
            mails.forEach { mail ->
                assertEquals(
                    "recruiter@example.com",
                    mail.jsonObject
                        .getValue("sender")
                        .jsonPrimitive.content,
                )
                assertEquals(
                    "WORTH_FOLLOW_UP",
                    mail.jsonObject
                        .getValue("state")
                        .jsonPrimitive.content,
                )
            }
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
    fun keepsTheRunGoingWhenTheWebhookCannotBeReached() =
        runTest {
            val sink = StringBuilder()
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath == "/hook") {
                        throw IOException("connection refused")
                    }
                    respond(content = contentFor(request), status = HttpStatusCode.OK, headers = jsonHeaders())
                }

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
