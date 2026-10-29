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

            val notRecruiter = assertIs<RecruiterVerification.NotRecruiter>(answer)
            assertTrue(notRecruiter.reason.contains("job listing"))
        }

    @Test
    fun readsTheAnswerOfAJobListing() =
        runTest {
            val answer =
                verifier(answering("not from a recruiter", listing = "a job listing"))
                    .verify(message())

            assertEquals(RecruiterVerification.JobListing, answer)
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
    fun aModelThatIsNotSureAboutTheJobListingFiltersTheMessageOut() =
        runTest {
            val answer =
                verifier(answering("not from a recruiter", listingConfidence = 0.2))
                    .verify(message())

            val notRecruiter = assertIs<RecruiterVerification.NotRecruiter>(answer)
            assertTrue(notRecruiter.reason.contains("not sure about the job listing"))
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
    fun aModelThatIsNotReachableForTheJobListingFiltersTheMessageOut() =
        runTest {
            val engine = answering("not from a recruiter", failingOnTheSecondRequest = true)

            val answer = verifier(engine).verify(message())

            val notRecruiter = assertIs<RecruiterVerification.NotRecruiter>(answer)
            assertTrue(notRecruiter.reason.contains("could not be reached"))
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
    fun asksTheModelAboutTheJobListingOnlyWhenItIsNoRecruiterMail() =
        runTest {
            val recruiter = answering("from a recruiter")
            verifier(recruiter).verify(message())

            assertEquals(1, recruiter.requestHistory.size)

            val listing = answering("not from a recruiter")
            verifier(listing).verify(message())

            assertEquals(2, listing.requestHistory.size)
            val body = (listing.requestHistory.last().body as TextContent).text
            assertTrue(body.contains("job_listing"))
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
        listing: String = DecisionModelVerifier.ANSWER_NOT_A_JOB_LISTING,
        listingConfidence: Double = confidence,
        failingOnTheSecondRequest: Boolean = false,
    ): MockEngine {
        var request = 0
        return MockEngine { httpRequest ->
            request++
            if (failingOnTheSecondRequest && request > 1) {
                throw IOException("the network is down")
            }
            val body = (httpRequest.body as TextContent).text
            val questions =
                Json
                    .parseToJsonElement(body)
                    .jsonObject["questions"]!!
                    .jsonObject.keys
            val answers =
                if (answer == null) {
                    ""
                } else {
                    questions.joinToString(",") { name ->
                        val choice =
                            if (name == DecisionModelVerifier.QUESTION) answer else listing
                        val sure =
                            if (name == DecisionModelVerifier.QUESTION) confidence else listingConfidence
                        """"$name":{"type":"choice","choice":"$choice","confidence":$sure}"""
                    }
                }
            respond(
                content = """{"answers":{$answers},"usage":{"input_tokens":10,"output_tokens":1}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
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
