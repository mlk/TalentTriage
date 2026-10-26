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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaChatClientTest {
    @Test
    fun sendsTheRequestToTheChatEndpoint() =
        runTest {
            val engine = engine()

            client(engine).chat(request())

            val sent = engine.requestHistory.single()
            assertEquals("http://ollama.example:11434/api/chat", sent.url.toString())
            assertEquals("POST", sent.method.value)
            assertEquals(ContentType.Application.Json.toString(), sent.body.contentType?.toString())
        }

    @Test
    fun serialisesTheRequest() =
        runTest {
            val engine = engine()

            client(engine).chat(request())

            val body = (engine.requestHistory.single().body as TextContent).text
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("llama3.2:3b", json["model"]?.jsonPrimitive?.content)
            assertEquals(false, json["stream"]?.jsonPrimitive?.content?.toBoolean())
            val messages = json["messages"]!!.jsonArray.map { it.jsonObject }
            assertEquals(2, messages.size)
            assertEquals("user", messages.first()["role"]?.jsonPrimitive?.content)
            assertEquals("Hello", messages.first()["content"]?.jsonPrimitive?.content)
            assertEquals("assistant", messages.last()["role"]?.jsonPrimitive?.content)
        }

    @Test
    fun sendsTheOptionsOfTheRequest() =
        runTest {
            val engine = engine()

            client(engine).chat(
                request().copy(options = OllamaOptions(temperature = 0.2, numCtx = 4096, numPredict = 512)),
            )

            val body = (engine.requestHistory.single().body as TextContent).text
            val options = Json.parseToJsonElement(body).jsonObject["options"]!!.jsonObject
            assertEquals(0.2, options["temperature"]?.jsonPrimitive?.content?.toDouble())
            assertEquals(4096, options["num_ctx"]?.jsonPrimitive?.content?.toInt())
            assertEquals(512, options["num_predict"]?.jsonPrimitive?.content?.toInt())
        }

    @Test
    fun deserialisesTheAnswer() =
        runTest {
            val engine = engine()

            val response = client(engine).chat(request())

            assertEquals("llama3.2:3b", response.model)
            assertEquals("Hi there", response.content)
            assertEquals("assistant", response.message?.role)
            assertTrue(response.done)
            assertEquals(42, response.evalCount)
            assertEquals(1_000_000L, response.totalDuration)
        }

    @Test
    fun usesTheConfiguredBaseUrl() =
        runTest {
            val engine = engine()

            client(engine, baseUrl = "http://airllm.example:8000/").chat(request())

            assertEquals(
                "http://airllm.example:8000/api/chat",
                engine.requestHistory
                    .single()
                    .url
                    .toString(),
            )
        }

    @Test
    fun failsWhenTheCallerAsksForAStreamedAnswer() =
        runTest {
            val engine = engine()

            assertFailsWith<IllegalArgumentException> {
                client(engine).chat(request().copy(stream = true))
            }
        }

    @Test
    fun failsWhenTheClientHasNoTimeoutPlugin() {
        val engine = engine()

        assertFailsWith<IllegalArgumentException> {
            OllamaChatClient(httpClient = HttpClient(engine) { install(ContentNegotiation) { json(JSON) } })
        }
    }

    @Test
    fun failsWhenTheClientHasNoContentNegotiation() {
        val engine = engine()

        assertFailsWith<IllegalArgumentException> {
            OllamaChatClient(httpClient = HttpClient(engine) { install(HttpTimeout) })
        }
    }

    @Test
    fun failsWhenTheApiAnswersWithAnError() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":"model 'llama3.2:3b' not found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = jsonHeaders(),
                    )
                }

            val error = assertFailsWith<OllamaException> { client(engine).chat(request()) }

            assertEquals(404, error.statusCode)
            assertTrue(error.body.contains("not found"))
        }

    private fun engine(): MockEngine =
        MockEngine {
            respond(
                content =
                    """
                    {
                      "model": "llama3.2:3b",
                      "created_at": "2026-10-24T08:00:00Z",
                      "message": {"role": "assistant", "content": "Hi there"},
                      "done": true,
                      "total_duration": 1000000,
                      "eval_count": 42,
                      "prompt_eval_count": 7
                    }
                    """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

    private fun client(
        engine: MockEngine,
        baseUrl: String = "http://ollama.example:11434",
        timeoutMillis: Long = 30_000,
    ): OllamaChatClient =
        OllamaChatClient(
            httpClient =
                HttpClient(engine) {
                    install(HttpTimeout)
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                encodeDefaults = true
                            },
                        )
                    }
                },
            baseUrl = baseUrl,
            timeoutMillis = timeoutMillis,
        )

    private fun request(): OllamaChatRequest =
        OllamaChatRequest(
            model = "llama3.2:3b",
            messages =
                listOf(
                    OllamaMessage(role = OllamaMessage.ROLE_USER, content = "Hello"),
                    OllamaMessage(role = OllamaMessage.ROLE_ASSISTANT, content = "Hi"),
                ),
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
