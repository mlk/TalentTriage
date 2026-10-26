package uk.me.michaellloydlee.recruiter.application.screening

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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatModelVerifierTest {
    @Test
    fun readsTheYesOfTheModel() =
        runTest {
            val answer = chatVerifier(modelAnswering("yes")).verify(message())

            assertEquals(RecruiterVerification.Recruiter, answer)
        }

    @Test
    fun readsTheYesOfTheModelWithExtraText() =
        runTest {
            val answer = chatVerifier(modelAnswering("Yes, this is a recruiter mail")).verify(message())

            assertEquals(RecruiterVerification.Recruiter, answer)
        }

    @Test
    fun readsTheYesOfTheModelInAnotherCase() =
        runTest {
            val answer = chatVerifier(modelAnswering("YES")).verify(message())

            assertEquals(RecruiterVerification.Recruiter, answer)
        }

    @Test
    fun readsTheNoOfTheModel() =
        runTest {
            val answer = chatVerifier(modelAnswering("no")).verify(message())

            assertEquals(RecruiterVerification.NotRecruiter, answer)
        }

    @Test
    fun anEmptyAnswerIsNoAnswer() =
        runTest {
            val answer = chatVerifier(modelAnswering("   ")).verify(message())

            assertIs<RecruiterVerification.Unknown>(answer)
        }

    @Test
    fun anAnswerThatIsNeitherYesNorNoIsNoAnswer() =
        runTest {
            val answer = chatVerifier(modelAnswering("Unsubscribe: <https://example.com>")).verify(message())

            val unknown = assertIs<RecruiterVerification.Unknown>(answer)
            assertTrue(unknown.reason.contains("Unsubscribe"))
        }

    @Test
    fun anErrorOfTheApiLeavesTheMessageAlone() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            val answer = chatVerifier(engine).verify(message())

            val unknown = assertIs<RecruiterVerification.Unknown>(answer)
            assertTrue(unknown.reason.contains("model not found"))
        }

    @Test
    fun asksTheModelWithTheSubjectAndTheBody() =
        runTest {
            val engine = modelAnswering("yes")

            chatVerifier(engine).verify(message(subject = "Your CV", body = "We are looking for a developer"))

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("Your CV"))
            assertTrue(body.contains("We are looking for a developer"))
        }

    @Test
    fun asksTheModelWithoutAnyRandomness() =
        runTest {
            val engine = modelAnswering("yes")

            chatVerifier(engine).verify(message())

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("\"temperature\":0.0"))
        }

    @Test
    fun cutsTheBodyOfTheMessageOff() =
        runTest {
            val engine = modelAnswering("yes")

            chatVerifier(engine, maxBodyLength = 10)
                .verify(message(subject = "Your CV", body = "a body that is much too long"))

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("a body tha"))
            assertTrue(!body.contains("much too long"))
        }

    private fun chatVerifier(
        engine: MockEngine,
        maxBodyLength: Int = ChatModelVerifier.DEFAULT_MAX_BODY_LENGTH,
    ): ChatModelVerifier =
        ChatModelVerifier(
            client =
                OllamaChatClient(
                    httpClient =
                        HttpClient(engine) {
                            install(HttpTimeout)
                            install(ContentNegotiation) { json(JSON) }
                        },
                ),
            maxBodyLength = maxBodyLength,
        )

    private fun modelAnswering(answer: String): MockEngine =
        MockEngine {
            respond(
                content = """{"message":{"role":"assistant","content":"$answer"},"done":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun message(
        subject: String = "",
        body: String = "",
    ): EmailMessage =
        EmailMessage(
            id = "message-id",
            threadId = "thread-id",
            sender = "someone@example.com",
            subject = subject,
            body = body,
            labelIds = listOf("INBOX"),
        )

    companion object {
        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
