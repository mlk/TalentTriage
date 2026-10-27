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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DecisionModelVerifierTest {
    @Test
    fun readsTheAnswerOfTheModel() =
        runTest {
            val answer = verifier(answering("from a recruiter")).verify(message())

            assertEquals(RecruiterVerification.Recruiter, answer)
        }

    @Test
    fun readsTheAnswerOfAMailThatIsNotFromARecruiter() =
        runTest {
            val answer = verifier(answering("not from a recruiter")).verify(message())

            assertEquals(RecruiterVerification.NotRecruiter, answer)
        }

    @Test
    fun theUnknownAnswerLeavesTheMessageAlone() =
        runTest {
            val answer = verifier(answering("unknown")).verify(message())

            val unknown = assertIs<RecruiterVerification.Unknown>(answer)
            assertTrue(unknown.reason.contains("not sure"))
        }

    @Test
    fun anAnswerOutsideTheThreeCriteriaLeavesTheMessageAlone() =
        runTest {
            val answer = verifier(answering("a spam mail")).verify(message())

            val unknown = assertIs<RecruiterVerification.Unknown>(answer)
            assertTrue(unknown.reason.contains("a spam mail"))
        }

    @Test
    fun aModelThatIsNotSureLeavesTheMessageAlone() =
        runTest {
            val answer = verifier(answering("from a recruiter", confidence = 0.2)).verify(message())

            val unknown = assertIs<RecruiterVerification.Unknown>(answer)
            assertTrue(unknown.reason.contains("not sure enough"))
        }

    @Test
    fun aModelWithoutAnAnswerLeavesTheMessageAlone() =
        runTest {
            val answer = verifier(answering(null)).verify(message())

            val unknown = assertIs<RecruiterVerification.Unknown>(answer)
            assertTrue(unknown.reason.contains("did not answer"))
        }

    @Test
    fun anErrorOfTheApiLeavesTheMessageAlone() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            val answer = verifier(engine).verify(message())

            val unknown = assertIs<RecruiterVerification.Unknown>(answer)
            assertTrue(unknown.reason.contains("model not found"))
        }

    @Test
    fun aNetworkFailureLeavesTheMessageAlone() =
        runTest {
            val engine = MockEngine { throw IOException("the network is down") }

            val answer = verifier(engine).verify(message())

            val unknown = assertIs<RecruiterVerification.Unknown>(answer)
            assertTrue(unknown.reason.contains("could not be reached"))
        }

    @Test
    fun asksTheModelWithTheSubjectAndTheBody() =
        runTest {
            val engine = answering("from a recruiter")

            verifier(engine).verify(message(subject = "Your CV", body = "We are looking for a developer"))

            val request = engine.requestHistory.single()
            assertEquals("/v1/systemone", request.url.encodedPath)
            val body = (request.body as TextContent).text
            assertTrue(body.contains("Your CV"))
            assertTrue(body.contains("We are looking for a developer"))
        }

    @Test
    fun asksTheModelWithTheThreeCriteria() =
        runTest {
            val engine = answering("from a recruiter")

            verifier(engine).verify(message())

            val body = (engine.requestHistory.single().body as TextContent).text
            val question =
                Json
                    .parseToJsonElement(body)
                    .jsonObject["questions"]!!
                    .jsonObject["recruiter"]!!
                    .jsonObject
            val criteria = question["criteria"]!!.jsonObject
            assertEquals(
                listOf("from a recruiter", "not from a recruiter", "unknown"),
                criteria.keys.toList(),
            )
        }

    @Test
    fun cutsTheBodyOfTheMessageOff() =
        runTest {
            val engine = answering("from a recruiter")

            verifier(engine, maxBodyLength = 10)
                .verify(message(subject = "Your CV", body = "a body that is much too long"))

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("a body tha"))
            assertTrue(!body.contains("much too long"))
        }

    private fun verifier(
        engine: MockEngine,
        maxBodyLength: Int = DecisionModelVerifier.DEFAULT_MAX_BODY_LENGTH,
    ): DecisionModelVerifier =
        DecisionModelVerifier(
            client =
                OllamaDecisionClient(
                    httpClient =
                        HttpClient(engine) {
                            install(HttpTimeout)
                            install(ContentNegotiation) { json(JSON) }
                        },
                ),
            maxBodyLength = maxBodyLength,
        )

    private fun answering(
        answer: String?,
        confidence: Double = 0.9,
    ): MockEngine =
        MockEngine {
            val answers =
                if (answer == null) {
                    "{}"
                } else {
                    """
                    {"answers":{"recruiter":{"type":"choice","choice":"$answer",
                    "probabilities":{"from a recruiter":0.9,"not from a recruiter":0.05,"unknown":0.05},
                    "confidence":$confidence}}}
                    """.trimIndent()
                }
            respond(
                content = answers,
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
