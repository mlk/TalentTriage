package uk.me.michaellloydlee.recruiter.application.requirements

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
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaWebSearchClient
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SalaryScaleLookupTest {
    @Test
    fun searchesForThePayOfTheRoleInTheRegion() =
        runTest {
            val engine = engine(PAGES)

            lookup(engine).scale(PROFILE)

            val body = (engine.requestHistory.single().body as TextContent).text
            val query =
                Json
                    .parseToJsonElement(body)
                    .jsonObject["query"]
                    ?.jsonPrimitive
                    ?.content
            assertEquals("average salary Senior Kotlin engineer London, United Kingdom", query)
        }

    @Test
    fun buildsTheScaleAroundThePayOfTheMarket() =
        runTest {
            val engine = engine(PAGES)

            val outcome = lookup(engine).scale(PROFILE)

            val market = assertIs<SalaryScaleOutcome.FromMarket>(outcome)
            assertEquals(SalaryAmount(75_000.0, "GBP"), market.market)
            assertEquals(
                listOf(53_000.0, 68_000.0, 83_000.0, 101_000.0),
                market.scale.bands.map { it.pay.amount },
            )
            assertEquals(
                listOf("junior", "mid level", "senior", "lead"),
                market.scale.bands.map { it.level },
            )
            assertTrue(market.scale.bands.all { it.pay.currency == "GBP" })
        }

    @Test
    fun usesTheDefaultScaleWhenTheWebCannotBeReached() =
        runTest {
            val engine = MockEngine { throw IOException("the network is down") }

            val outcome = lookup(engine).scale(PROFILE)

            val fallback = assertIs<SalaryScaleOutcome.Default>(outcome)
            assertTrue(fallback.reason.contains("could not be reached"))
            assertEquals(4, fallback.scale.bands.size)
            assertTrue(fallback.scale.bands.all { it.pay.currency == "GBP" })
        }

    @Test
    fun usesTheDefaultScaleWhenThePagesHoldNoPay() =
        runTest {
            val engine =
                engine("""{"results":[{"title":"Jobs","url":"https://jobs.example","content":"No pay here"}]}""")

            val outcome = lookup(engine).scale(PROFILE)

            val fallback = assertIs<SalaryScaleOutcome.Default>(outcome)
            assertTrue(fallback.reason.contains("no pay of the market"))
        }

    private fun engine(pages: String): MockEngine =
        MockEngine {
            respond(
                content = pages,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun lookup(engine: MockEngine): SalaryScaleLookup =
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
        )

    companion object {
        private val PROFILE =
            CvProfile(
                profile = "Senior Kotlin engineer",
                role = "Senior Kotlin engineer",
                region = "London, United Kingdom",
            )

        private const val PAGES =
            """{"results":[
              {"title":"Kotlin engineer salary in London",
               "url":"https://salary.example/kotlin",
               "content":"The median salary is £75,000 a year, the range is £60,000 to £90,000"}
            ]}"""

        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
