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
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OllamaWebSearchClientTest {
    @Test
    fun sendsTheQueryToTheSearchEndpoint() =
        runTest {
            val engine = engine()

            client(engine).search("average salary kotlin engineer london")

            val sent = engine.requestHistory.single()
            assertEquals("https://ollama.example/api/web_search", sent.url.toString())
            assertEquals("POST", sent.method.value)
            assertEquals("Bearer the-key", sent.headers[HttpHeaders.Authorization])
            val body = (sent.body as TextContent).text
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("average salary kotlin engineer london", json["query"]?.jsonPrimitive?.content)
            assertEquals(5, json["max_results"]?.jsonPrimitive?.content?.toInt())
        }

    @Test
    fun deserialisesThePagesOfTheSearch() =
        runTest {
            val outcome = client(engine()).search("average salary kotlin engineer london")

            val pages = assertIs<WebSearchOutcome.Found>(outcome).results
            assertEquals(1, pages.size)
            assertEquals("Kotlin engineer salary", pages.first().title)
            assertEquals("https://salary.example/kotlin", pages.first().url)
            assertTrue(pages.first().content.contains("£75,000"))
        }

    @Test
    fun failsWithoutAnApiKey() =
        runTest {
            val engine = engine()

            val outcome = client(engine, apiKey = null).search("average salary")

            val failure = assertIs<WebSearchOutcome.Failed>(outcome)
            assertTrue(failure.reason.contains("OLLAMA_API_KEY"))
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun failsWhenTheApiAnswersWithAnError() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"error":"unauthorized"}""", status = HttpStatusCode.Unauthorized)
                }

            val outcome = client(engine).search("average salary")

            val failure = assertIs<WebSearchOutcome.Failed>(outcome)
            assertTrue(failure.reason.contains("401"))
        }

    @Test
    fun failsWhenTheApiCannotBeReached() =
        runTest {
            val engine = MockEngine { throw IOException("the network is down") }

            val outcome = client(engine).search("average salary")

            val failure = assertIs<WebSearchOutcome.Failed>(outcome)
            assertTrue(failure.reason.contains("could not be reached"))
        }

    private fun engine(): MockEngine =
        MockEngine {
            respond(
                content =
                    """
                    {"results":[
                      {"title":"Kotlin engineer salary",
                       "url":"https://salary.example/kotlin",
                       "content":"The median salary of a Kotlin engineer is £75,000 a year"}
                    ]}
                    """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun client(
        engine: MockEngine,
        apiKey: String? = "the-key",
    ): OllamaWebSearchClient =
        OllamaWebSearchClient(
            httpClient =
                HttpClient(engine) {
                    install(HttpTimeout)
                    install(ContentNegotiation) { json(JSON) }
                },
            apiKey = apiKey,
            baseUrl = "https://ollama.example",
        )

    companion object {
        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
