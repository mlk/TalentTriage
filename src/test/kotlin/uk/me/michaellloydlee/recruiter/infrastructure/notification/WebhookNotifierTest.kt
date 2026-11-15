package uk.me.michaellloydlee.recruiter.infrastructure.notification

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebhookNotifierTest {
    @Test
    fun sendsTheMailsThatNeedTheJobSeekerAsJson() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = "", status = HttpStatusCode.OK)
                }

            val outcome =
                withNotifier(engine) { notifier ->
                    notifier.notify(
                        listOf(
                            NotificationMail(
                                id = "message-id",
                                sender = "recruiter@example.com",
                                subject = "Your CV",
                                state = "WORTH_FOLLOW_UP",
                            ),
                        ),
                    )
                }

            assertEquals(WebhookOutcome.Sent, outcome)
            val sent = engine.requestHistory.single()
            assertEquals("POST", sent.method.value)
            assertEquals("http://webhook.example/hook", sent.url.toString())
            assertEquals(
                ContentType.Application.Json.toString(),
                sent.body.contentType?.toString(),
            )
            val body = Json.parseToJsonElement((sent.body as TextContent).text).jsonObject
            val mail = body["mails"]!!.jsonArray.single().jsonObject
            assertEquals("message-id", mail.getValue("id").jsonPrimitive.content)
            assertEquals("recruiter@example.com", mail.getValue("sender").jsonPrimitive.content)
            assertEquals("Your CV", mail.getValue("subject").jsonPrimitive.content)
            assertEquals("WORTH_FOLLOW_UP", mail.getValue("state").jsonPrimitive.content)
        }

    @Test
    fun reportsAWebhookThatAnswersWithAnError() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = "boom",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            val outcome = withNotifier(engine) { notifier -> notifier.notify(listOf(mail())) }

            val failed = assertIs<WebhookOutcome.Failed>(outcome)
            assertTrue(failed.reason.contains("500"))
        }

    @Test
    fun reportsAWebhookThatAnswersWithABodyTooLargeToRead() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = "x".repeat(MAX_BODY),
                        status = HttpStatusCode.InternalServerError,
                    )
                }

            val outcome = withNotifier(engine) { notifier -> notifier.notify(listOf(mail())) }

            val failed = assertIs<WebhookOutcome.Failed>(outcome)
            assertTrue(failed.reason.contains("500"))
            assertTrue(failed.reason.length < MAX_BODY)
        }

    @Test
    fun reportsAWebhookThatTimesOut() =
        runTest {
            val engine =
                MockEngine {
                    delay(TOO_LONG)
                    respond(content = "", status = HttpStatusCode.OK)
                }

            val outcome =
                HttpClient(engine) {
                    install(HttpTimeout) { this.requestTimeoutMillis = LONG_TIMEOUT }
                    install(ContentNegotiation) { json(JSON) }
                }.use { client ->
                    WebhookNotifier(
                        httpClient = client,
                        url = "http://webhook.example/hook",
                        timeoutMillis = TIMEOUT,
                    ).notify(listOf(mail()))
                }

            val failed = assertIs<WebhookOutcome.Failed>(outcome)
            assertTrue(failed.reason.contains("timeout", ignoreCase = true), failed.reason)
        }

    @Test
    fun reportsAWebhookThatCouldNotBeReached() =
        runTest {
            val engine =
                MockEngine {
                    throw IOException("connection refused")
                }

            val outcome = withNotifier(engine) { notifier -> notifier.notify(listOf(mail())) }

            val failed = assertIs<WebhookOutcome.Failed>(outcome)
            assertTrue(failed.reason.contains("connection refused"))
        }

    @Test
    fun failsWhenTheClientHasNoTimeoutPlugin() {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }

        HttpClient(engine) { install(ContentNegotiation) { json(JSON) } }.use { client ->
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                WebhookNotifier(
                    httpClient = client,
                    url = "http://webhook.example/hook",
                )
            }
        }
    }

    private fun mail(): NotificationMail =
        NotificationMail(
            id = "message-id",
            sender = "recruiter@example.com",
            subject = "Your CV",
            state = "WORTH_FOLLOW_UP",
        )

    private suspend fun <T> withNotifier(
        engine: MockEngine,
        block: suspend (WebhookNotifier) -> T,
    ): T =
        HttpClient(engine) {
            install(HttpTimeout)
            install(ContentNegotiation) { json(JSON) }
        }.use { client ->
            block(WebhookNotifier(httpClient = client, url = "http://webhook.example/hook"))
        }

    companion object {
        private val JSON: Json = Json { ignoreUnknownKeys = true }

        private const val TIMEOUT: Long = 1_000

        private const val LONG_TIMEOUT: Long = 120_000

        private const val TOO_LONG: Long = 60_000

        private const val MAX_BODY: Int = 8_192
    }
}
