package uk.me.michaellloydlee.recruiter.infrastructure.llm

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaDecisionClientTest {
    @Test
    fun sendsTheRequestToTheDecisionEndpoint() =
        runTest {
            val engine = engine()

            client(engine).decide(request())

            val sent = engine.requestHistory.single()
            assertEquals("http://ollama.example:11434/v1/systemone", sent.url.toString())
            assertEquals("POST", sent.method.value)
            assertEquals(ContentType.Application.Json.toString(), sent.body.contentType?.toString())
        }

    @Test
    fun serialisesTheRequest() =
        runTest {
            val engine = engine()

            client(engine).decide(request())

            val body = (engine.requestHistory.single().body as TextContent).text
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("clef:cloud", json["model"]?.jsonPrimitive?.content)
            assertEquals("the text to classify", json["state"]?.jsonPrimitive?.content)
            val question = json["questions"]!!.jsonObject["recruiter"]!!.jsonObject
            assertEquals("choice", question["type"]?.jsonPrimitive?.content)
            assertEquals(3, question["criteria"]!!.jsonObject.size)
        }

    @Test
    fun deserialisesTheAnswer() =
        runTest {
            val response =
                client(engine()).decide(
                    request().copy(
                        questions =
                            mapOf(
                                "recruiter" to
                                    OllamaDecisionQuestion(
                                        instructions = "Which one?",
                                        criteria = mapOf("from a recruiter" to "a recruiter"),
                                    ),
                            ),
                    ),
                )

            assertEquals("clef:cloud", response.model)
            val answer = response.answers.getValue("recruiter")
            assertEquals("from a recruiter", answer.choice)
            assertEquals(0.9, answer.confidence)
            assertEquals(0.9, answer.probabilities.getValue("from a recruiter"))
            assertEquals(174, response.usage.inputTokens)
        }

    @Test
    fun failsWhenTheApiAnswersWithAnError() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            val error = assertFailsWith<OllamaException> { client(engine).decide(request()) }

            assertEquals(404, error.statusCode)
            assertTrue(error.body.contains("model not found"))
        }

    @Test
    fun failsWithAReadableErrorWhenTheApiAnswersWithSomethingElse() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = "not json at all",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            val error = assertFailsWith<OllamaException> { client(engine).decide(request()) }

            assertTrue(error.body.contains("text/plain"))
        }

    private fun engine(): MockEngine =
        MockEngine {
            respond(
                content =
                    """
                    {"model":"clef:cloud",
                     "answers":{"recruiter":{"type":"choice","choice":"from a recruiter",
                     "probabilities":{"from a recruiter":0.9},"confidence":0.9}},
                     "usage":{"input_tokens":174,"output_tokens":1}}
                    """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun client(engine: MockEngine): OllamaDecisionClient =
        OllamaDecisionClient(
            httpClient =
                HttpClient(engine) {
                    install(HttpTimeout)
                    install(ContentNegotiation) { json(JSON) }
                },
            baseUrl = "http://ollama.example:11434",
        )

    private fun request(): OllamaDecisionRequest =
        OllamaDecisionRequest(
            model = "clef:cloud",
            state = "the text to classify",
            questions =
                mapOf(
                    "recruiter" to
                        OllamaDecisionQuestion(
                            instructions = "Which one?",
                            criteria =
                                mapOf(
                                    "from a recruiter" to "a recruiter",
                                    "not from a recruiter" to "no recruiter",
                                    "unknown" to "cannot tell",
                                ),
                        ),
                ),
        )

    companion object {
        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
