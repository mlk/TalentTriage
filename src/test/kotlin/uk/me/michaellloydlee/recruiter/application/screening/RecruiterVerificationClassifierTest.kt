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

class RecruiterVerificationClassifierTest {
    @Test
    fun readsTheYesOfTheModel() =
        runTest {
            val classifier = classifier(modelAnswering("yes"))

            val answer = classifier.classify(message())

            assertEquals(LocalClassification.Recruiter, answer)
        }

    @Test
    fun readsTheYesOfTheModelWithExtraText() =
        runTest {
            val classifier = classifier(modelAnswering("Yes, this is a recruiter mail"))

            val answer = classifier.classify(message())

            assertEquals(LocalClassification.Recruiter, answer)
        }

    @Test
    fun readsTheYesOfTheModelInAnotherCase() =
        runTest {
            val classifier = classifier(modelAnswering("YES"))

            val answer = classifier.classify(message())

            assertEquals(LocalClassification.Recruiter, answer)
        }

    @Test
    fun asksTheModelWithoutAnyRandomness() =
        runTest {
            val engine = modelAnswering("yes")

            classifier(engine).classify(message())

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("\"temperature\":0.0"))
        }

    @Test
    fun readsTheNoOfTheModel() =
        runTest {
            val classifier = classifier(modelAnswering("no"))

            val answer = classifier.classify(message())

            assertEquals(LocalClassification.NotRecruiter, answer)
        }

    @Test
    fun anEmptyAnswerIsNoAnswer() =
        runTest {
            val classifier = classifier(modelAnswering("   "))

            val answer = classifier.classify(message())

            assertIs<LocalClassification.Unavailable>(answer)
        }

    @Test
    fun anAnswerThatIsNeitherYesNorNoIsNoAnswer() =
        runTest {
            val classifier = classifier(modelAnswering("Unsubscribe: <https://www.linkedin.com/help/s>"))

            val answer = classifier.classify(message())

            val unavailable = assertIs<LocalClassification.Unavailable>(answer)
            assertTrue(unavailable.reason.contains("Unsubscribe"))
        }

    @Test
    fun anErrorOfTheApiLeavesTheMessageAlone() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            val answer = classifier(engine).classify(message())

            val unavailable = assertIs<LocalClassification.Unavailable>(answer)
            assertTrue(unavailable.reason.contains("model not found"))
        }

    @Test
    fun asksTheModelWithTheSubjectAndTheBody() =
        runTest {
            val engine = modelAnswering("yes")

            classifier(engine).classify(message(subject = "Your CV", body = "We are looking for a developer"))

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("Your CV"))
            assertTrue(body.contains("We are looking for a developer"))
            assertTrue(body.contains("minimax-m3.1-flash:cloud"))
        }

    @Test
    fun cutsTheBodyOfTheMessageOff() =
        runTest {
            val engine = modelAnswering("yes")

            classifier(engine, maxBodyLength = 10)
                .classify(message(subject = "Your CV", body = "a body that is much too long"))

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("a body tha"))
            assertTrue(!body.contains("much too long"))
        }

    private fun classifier(
        engine: MockEngine,
        maxBodyLength: Int = RecruiterVerificationClassifier.DEFAULT_MAX_BODY_LENGTH,
    ): RecruiterVerificationClassifier =
        RecruiterVerificationClassifier(
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
