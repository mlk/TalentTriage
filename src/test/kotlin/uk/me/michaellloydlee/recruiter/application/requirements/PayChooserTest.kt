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
import uk.me.michaellloydlee.recruiter.domain.requirements.PayBand
import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import uk.me.michaellloydlee.recruiter.domain.requirements.PayScale
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PayChooserTest {
    @Test
    fun theModelNamesThePayOfTheScale() =
        runTest {
            val engine = engine(answer("1", confidence = 0.9))

            val outcome = chooser(engine).choose(CV, SCALE)

            val chosen = assertIs<PayOutcome.Chosen>(outcome)
            assertEquals(PayRequirement(70_000.0, "GBP"), chosen.pay)
        }

    @Test
    fun theQuestionHoldsThePaysOfTheScale() =
        runTest {
            val engine = engine(answer("1", confidence = 0.9))

            chooser(engine).choose(CV, SCALE)

            val body = (engine.requestHistory.single().body as TextContent).text
            val question =
                Json
                    .parseToJsonElement(body)
                    .jsonObject["questions"]!!
                    .jsonObject["minimum_pay"]!!
                    .jsonObject
            assertEquals("choice", question["type"]?.jsonPrimitive?.content)
            val criteria = question["criteria"]!!.jsonObject
            assertEquals(
                "60000 GBP per year, the pay of the market of a junior role",
                criteria["0"]?.jsonPrimitive?.content,
            )
            assertEquals(
                "90000 GBP per year, the pay of the market of a lead role",
                criteria["2"]?.jsonPrimitive?.content,
            )
        }

    @Test
    fun aModelThatIsNotSureNamesAPay() =
        runTest {
            val engine = engine(answer("1", confidence = 0.3))

            val outcome = chooser(engine).choose(CV, SCALE)

            val chosen = assertIs<PayOutcome.Chosen>(outcome)
            assertEquals(PayRequirement(70_000.0, "GBP"), chosen.pay)
        }

    @Test
    fun aModelThatAnswersWithAnUnknownOptionNamesNoPay() =
        runTest {
            val engine = engine(answer("nonsense", confidence = 0.9))

            val outcome = chooser(engine).choose(CV, SCALE)

            val unclear = assertIs<PayOutcome.Unclear>(outcome)
            assertTrue(unclear.reason.contains("unknown option"))
        }

    @Test
    fun aModelThatCannotBeReachedFails() =
        runTest {
            val engine = MockEngine { throw IOException("the network is down") }

            val outcome = chooser(engine).choose(CV, SCALE)

            val failure = assertIs<PayOutcome.Failed>(outcome)
            assertTrue(failure.reason.contains("could not be reached"))
        }

    private fun answer(
        choice: String,
        confidence: Double,
    ): String =
        """
        {"model":"clef:cloud",
         "answers":{"minimum_pay":{"type":"choice","choice":"$choice","confidence":$confidence}},
         "usage":{"input_tokens":10,"output_tokens":1}}
        """.trimIndent()

    private fun engine(answer: String): MockEngine =
        MockEngine {
            respond(
                content = answer,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun chooser(engine: MockEngine): PayChooser =
        PayChooser(
            client =
                OllamaDecisionClient(
                    httpClient =
                        HttpClient(engine) {
                            install(HttpTimeout)
                            install(ContentNegotiation) { json(JSON) }
                        },
                ),
        )

    companion object {
        private const val CV = "Michael Lloyd Lee is a senior Kotlin engineer of the payment team."

        private val SCALE =
            PayScale(
                listOf(
                    PayBand(PayRequirement(60_000.0, "GBP"), "junior"),
                    PayBand(PayRequirement(70_000.0, "GBP"), "mid level"),
                    PayBand(PayRequirement(90_000.0, "GBP"), "lead"),
                ),
            )

        private val JSON: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
