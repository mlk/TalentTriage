package uk.me.michaellloydlee.recruiter.application

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProcessInboxServiceNotificationTest {
    @Test
    fun notifiesTheWebhookAboutTheMailsThatNeedTheJobSeeker() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()
            val webhook = webhookEngine()

            val summary = service(gmail, model, notifier = webhookNotifier(webhook)).run(WINDOW)

            assertEquals(2, summary.worthFollowUp)
            assertEquals(1, webhook.requestHistory.size)
            val body = Json.parseToJsonElement((webhook.requestHistory.single().body as TextContent).text).jsonObject
            val mails = body["mails"]!!.jsonArray
            assertEquals(2, mails.size)
            assertEquals(
                setOf("first", "second"),
                mails
                    .map {
                        it.jsonObject
                            .getValue("id")
                            .jsonPrimitive.content
                    }.toSet(),
            )
            mails.forEach { mail ->
                assertEquals(
                    "Jane Recruiter <recruiter@example.com>",
                    mail.jsonObject
                        .getValue("sender")
                        .jsonPrimitive.content,
                )
                assertEquals(
                    "Your CV",
                    mail.jsonObject
                        .getValue("subject")
                        .jsonPrimitive.content,
                )
                assertEquals(
                    "WORTH_FOLLOW_UP",
                    mail.jsonObject
                        .getValue("state")
                        .jsonPrimitive.content,
                )
            }
        }

    @Test
    fun notifiesTheWebhookAboutTheMailsThatNeedMoreInformation() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(officeDays = "unclear")
            val webhook = webhookEngine()

            val summary = service(gmail, model, notifier = webhookNotifier(webhook)).run(WINDOW)

            assertEquals(2, summary.needsMoreInformation)
            val body = Json.parseToJsonElement((webhook.requestHistory.single().body as TextContent).text).jsonObject
            body["mails"]!!.jsonArray.forEach { mail ->
                assertEquals(
                    "NEEDS_MORE_INFORMATION",
                    mail.jsonObject
                        .getValue("state")
                        .jsonPrimitive.content,
                )
            }
        }

    @Test
    fun doesNotNotifyTheWebhookAboutTheMailsThatDoNotNeedTheJobSeeker() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine(jdMatches = "does not match")
            val webhook = webhookEngine()

            val summary = service(gmail, model, notifier = webhookNotifier(webhook)).run(WINDOW)

            assertEquals(2, summary.trashed)
            assertEquals(0, webhook.requestHistory.size)
        }

    @Test
    fun doesNotNotifyTheWebhookAboutAMailTheScreeningFilteredOut() =
        runTest {
            val gmail = gmailEngine()
            val model =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }
            val webhook = webhookEngine()

            val summary = service(gmail, model, notifier = webhookNotifier(webhook)).run(WINDOW)

            assertEquals(2, summary.unknown)
            assertEquals(0, webhook.requestHistory.size)
        }

    @Test
    fun sendsNothingToTheWebhookWhenNoWebhookIsConfigured() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()

            val summary = service(gmail, model).run(WINDOW)

            assertEquals(2, summary.worthFollowUp)
        }

    @Test
    fun notifiesAboutTheMailsScreenedBeforeAFailureAbortedTheRun() =
        runTest {
            val gmail = gmailEngine(failModifyFor = "second")
            val model = modelEngine()
            val webhook = webhookEngine()

            assertFailsWith<GmailApiException> {
                service(gmail, model, notifier = webhookNotifier(webhook)).run(WINDOW)
            }

            assertEquals(1, webhook.requestHistory.size)
            val body = Json.parseToJsonElement((webhook.requestHistory.single().body as TextContent).text).jsonObject
            val mails = body["mails"]!!.jsonArray
            assertEquals(
                listOf("first"),
                mails.map {
                    it.jsonObject
                        .getValue("id")
                        .jsonPrimitive.content
                },
            )
        }

    @Test
    fun keepsTheRunGoingWhenTheWebhookAnswersWithAnError() =
        runTest {
            val gmail = gmailEngine()
            val model = modelEngine()
            val webhook = webhookEngine(status = HttpStatusCode.InternalServerError)
            val log = StringBuilder()

            val summary = service(gmail, model, log, notifier = webhookNotifier(webhook)).run(WINDOW)

            assertEquals(2, summary.screened)
            assertEquals(2, summary.worthFollowUp)
            assertTrue(log.toString().contains("process-inbox.notification_failed"))
            assertTrue(log.toString().contains("\"ids\":\"first,second\""))
        }
}
