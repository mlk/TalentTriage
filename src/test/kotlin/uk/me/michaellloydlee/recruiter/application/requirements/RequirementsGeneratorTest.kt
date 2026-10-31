package uk.me.michaellloydlee.recruiter.application.requirements

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaWebSearchClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequirementsGeneratorTest {
    @Test
    fun generatesTheRequirementsFromTheCv() =
        runTest {
            val outcome = generator(engine()).generate(CV, 2)

            val generated = assertIs<RequirementsOutcome.Generated>(outcome)
            assertEquals("Senior Kotlin engineer of the payment team", generated.requirements.profile)
            assertEquals(2, generated.requirements.maximumOfficeDays)
            assertEquals(83_000.0, generated.requirements.minimumPay?.amount)
            assertEquals("GBP", generated.requirements.minimumPay?.currency)
        }

    @Test
    fun theDaysOfOfficeComeFromTheJobSeeker() =
        runTest {
            val outcome = generator(engine()).generate(CV, 4)

            val generated = assertIs<RequirementsOutcome.Generated>(outcome)
            assertEquals(4, generated.requirements.maximumOfficeDays)
        }

    @Test
    fun theNotesHoldThePayOfTheMarket() =
        runTest {
            val outcome = generator(engine()).generate(CV, 2)

            val generated = assertIs<RequirementsOutcome.Generated>(outcome)
            assertTrue(generated.notes.any { it.contains("75000.0 GBP") })
        }

    @Test
    fun failsWhenTheProfileCannotBeWritten() =
        runTest {
            val engine = engine(chatAnswer = "I am a senior Kotlin engineer.")

            val outcome = generator(engine).generate(CV, 2)

            val failure = assertIs<RequirementsOutcome.Failed>(outcome)
            assertTrue(failure.reason.contains("JSON object"))
        }

    @Test
    fun theFileHasNoPayWhenTheModelNamesNoPay() =
        runTest {
            val engine = engine(decisionAnswer = decisionAnswer("nonsense", confidence = 0.9))

            val outcome = generator(engine).generate(CV, 2)

            val generated = assertIs<RequirementsOutcome.Generated>(outcome)
            assertNull(generated.requirements.minimumPay)
            assertTrue(generated.notes.any { it.contains("minimum pay is not set") })
        }

    @Test
    fun theDefaultScaleIsUsedWhenTheWebCannotBeReached() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/api/web_search")) {
                        throw java.io.IOException("the network is down")
                    }
                    respond(answerFor(request), HttpStatusCode.OK, jsonHeaders())
                }

            val outcome = generator(engine).generate(CV, 2)

            val generated = assertIs<RequirementsOutcome.Generated>(outcome)
            assertTrue(generated.notes.any { it.contains("pays of the market are not known") })
        }

    private fun decisionAnswer(
        choice: String,
        confidence: Double,
    ): String =
        """
        {"model":"clef:cloud",
         "answers":{"minimum_pay":{"type":"choice","choice":"$choice","confidence":$confidence}},
         "usage":{"input_tokens":10,"output_tokens":1}}
        """.trimIndent()

    private fun engine(
        chatAnswer: String = PROFILE_ANSWER,
        decisionAnswer: String = decisionAnswer("2", confidence = 0.9),
    ): MockEngine =
        MockEngine { request ->
            respond(answerFor(request, chatAnswer, decisionAnswer), HttpStatusCode.OK, jsonHeaders())
        }

    private fun answerFor(
        request: HttpRequestData,
        chatAnswer: String = PROFILE_ANSWER,
        decisionAnswer: String = decisionAnswer("2", confidence = 0.9),
    ): String =
        when {
            request.url.encodedPath.endsWith("/api/chat") -> {
                """{"message":{"role":"assistant","content":"$chatAnswer"},"done":true}"""
            }

            request.url.encodedPath.endsWith("/api/web_search") -> {
                PAGES
            }

            else -> {
                decisionAnswer
            }
        }

    private fun generator(engine: MockEngine): RequirementsGenerator =
        RequirementsGenerator(
            profileWriter =
                CvProfileWriter(
                    client =
                        OllamaChatClient(
                            httpClient =
                                HttpClient(engine) {
                                    install(HttpTimeout)
                                    install(ContentNegotiation) { json(JSON) }
                                },
                        ),
                ),
            salaryLookup =
                SalaryScaleLookup(
                    search =
                        OllamaWebSearchClient(
                            httpClient =
                                HttpClient(engine) {
                                    install(HttpTimeout)
                                    install(ContentNegotiation) { json(JSON) }
                                },
                            apiKey = "the-key",
                        ),
                ),
            payChooser =
                PayChooser(
                    client =
                        OllamaDecisionClient(
                            httpClient =
                                HttpClient(engine) {
                                    install(HttpTimeout)
                                    install(ContentNegotiation) { json(JSON) }
                                },
                        ),
                ),
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private const val CV = "Michael Lloyd Lee is a senior Kotlin engineer of the payment team."

        private const val PROFILE_ANSWER =
            """{\"profile\":\"Senior Kotlin engineer of the payment team\",""" +
                """\"role\":\"Senior Kotlin engineer\",\"region\":\"London, United Kingdom\"}"""

        private const val PAGES =
            """{"results":[
              {"title":"Kotlin engineer salary in London",
               "url":"https://salary.example/kotlin",
               "content":"The median salary is £75,000 a year"}
            ]}"""

        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
