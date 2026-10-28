package uk.me.michaellloydlee.recruiter.application.evaluation

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
import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.evaluation.OfficeDays
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaDecisionClient
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DecisionModelEvaluatorTest {
    @Test
    fun asksEveryCriterionInOneRequest() =
        runTest {
            val engine = engine()

            evaluator(engine).evaluate(message(), "the job description", REQUIREMENTS)

            val request = engine.requestHistory.single()
            assertEquals("/v1/systemone", request.url.encodedPath)
            val questions =
                Json
                    .parseToJsonElement((request.body as TextContent).text)
                    .jsonObject["questions"]!!
                    .jsonObject
            assertEquals(
                listOf("jd_matches", "office_days", "pay_matches", "pay_missing"),
                questions.keys.sorted(),
            )
        }

    @Test
    fun asksTheRequirementsInTheQuestions() =
        runTest {
            val engine = engine()

            evaluator(engine).evaluate(message(), "the job description", REQUIREMENTS)

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("Senior Kotlin engineer"))
            assertTrue(body.contains("120000 EUR"))
            assertTrue(body.contains("at most 2 days"))
        }

    @Test
    fun leavesThePayAloneWhenItDoesNotMatter() =
        runTest {
            val engine = engine()

            evaluator(engine).evaluate(message(), "", REQUIREMENTS_WITHOUT_PAY)

            val questions =
                Json
                    .parseToJsonElement((engine.requestHistory.single().body as TextContent).text)
                    .jsonObject["questions"]!!
                    .jsonObject
            assertEquals(listOf("jd_matches", "office_days"), questions.keys.toList())
        }

    @Test
    fun readsTheAnswerOfTheModel() =
        runTest {
            val engine = engine()

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val evaluated = assertIs<JobDescriptionEvaluation.Evaluated>(evaluation)
            assertEquals(true, evaluated.jdMatches)
            assertEquals(OfficeDays.AT_MOST_THE_ALLOWED_DAYS, evaluated.officeDays)
            assertEquals(false, evaluated.payMissing)
        }

    @Test
    fun theQuestionOfTheOfficeDaysHasThreeAnswers() =
        runTest {
            val engine = engine()

            evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val questions =
                Json
                    .parseToJsonElement((engine.requestHistory.single().body as TextContent).text)
                    .jsonObject["questions"]!!
                    .jsonObject
            val options =
                questions["office_days"]!!
                    .jsonObject["criteria"]!!
                    .jsonObject
            assertEquals(
                listOf("does not match", "matches", "unclear"),
                options.keys.sorted(),
            )
        }

    @Test
    fun readsTheOfficeDaysThatAreUnclear() =
        runTest {
            val engine =
                engine(
                    answers =
                        mapOf(
                            "jd_matches" to "matches",
                            "office_days" to "unclear",
                        ),
                )

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS_WITHOUT_PAY)

            val evaluated = assertIs<JobDescriptionEvaluation.Evaluated>(evaluation)
            assertEquals(OfficeDays.UNCLEAR, evaluated.officeDays)
        }

    @Test
    fun readsTheOfficeDaysThatAreTooMany() =
        runTest {
            val engine =
                engine(
                    answers =
                        mapOf(
                            "jd_matches" to "matches",
                            "office_days" to "does not match",
                        ),
                )

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS_WITHOUT_PAY)

            val evaluated = assertIs<JobDescriptionEvaluation.Evaluated>(evaluation)
            assertEquals(OfficeDays.MORE_THAN_THE_ALLOWED_DAYS, evaluated.officeDays)
        }

    @Test
    fun aModelThatIsNotSureAboutTheOfficeDaysStillSaysThatTheyAreUnclear() =
        runTest {
            val engine =
                engine(
                    answers =
                        mapOf(
                            "jd_matches" to "matches",
                            "office_days" to "unclear",
                        ),
                    confidenceOfTheOfficeDays = 0.37,
                )

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS_WITHOUT_PAY)

            val evaluated = assertIs<JobDescriptionEvaluation.Evaluated>(evaluation)
            assertEquals(OfficeDays.UNCLEAR, evaluated.officeDays)
        }

    @Test
    fun anAnswerWithAnUnknownOptionLeavesTheMailAlone() =
        runTest {
            val engine =
                engine(
                    answers =
                        mapOf(
                            "jd_matches" to "matches",
                            "office_days" to "who knows",
                        ),
                )

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS_WITHOUT_PAY)

            val unknown = assertIs<JobDescriptionEvaluation.Unknown>(evaluation)
            assertTrue(unknown.reason.contains("unknown option"))
        }

    @Test
    fun aModelThatIsNotSureLeavesTheMailAlone() =
        runTest {
            val engine = engine(confidence = 0.2)

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val unknown = assertIs<JobDescriptionEvaluation.Unknown>(evaluation)
            assertTrue(unknown.reason.contains("not sure enough"))
        }

    @Test
    fun aModelThatDoesNotAnswerEveryQuestionLeavesTheMailAlone() =
        runTest {
            val engine = engine(answers = emptyMap())

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val unknown = assertIs<JobDescriptionEvaluation.Unknown>(evaluation)
            assertTrue(unknown.reason.contains("did not answer"))
        }

    @Test
    fun anErrorOfTheApiLeavesTheMailAlone() =
        runTest {
            val engine =
                MockEngine {
                    respond(content = """{"error":"model not found"}""", status = HttpStatusCode.NotFound)
                }

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val unknown = assertIs<JobDescriptionEvaluation.Unknown>(evaluation)
            assertTrue(unknown.reason.contains("model not found"))
        }

    @Test
    fun aNetworkFailureLeavesTheMailAlone() =
        runTest {
            val engine = MockEngine { throw IOException("the network is down") }

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val unknown = assertIs<JobDescriptionEvaluation.Unknown>(evaluation)
            assertTrue(unknown.reason.contains("could not be reached"))
        }

    private fun evaluator(
        engine: MockEngine,
        maxBodyLength: Int = DecisionModelEvaluator.DEFAULT_MAX_BODY_LENGTH,
    ): DecisionModelEvaluator =
        DecisionModelEvaluator(
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

    private fun engine(
        confidence: Double = 0.9,
        answers: Map<String, String> =
            mapOf(
                "pay_matches" to "does not match",
                "jd_matches" to "matches",
                "office_days" to "matches",
                "pay_missing" to "does not match",
            ),
        confidenceOfTheOfficeDays: Double = confidence,
    ): MockEngine =
        MockEngine {
            val answer =
                answers.entries.joinToString(",") { (name, choice) ->
                    val answerConfidence =
                        if (name == "office_days") confidenceOfTheOfficeDays else confidence
                    """"$name":{"type":"choice","choice":"$choice","confidence":$answerConfidence}"""
                }
            respond(
                content = DECISION_ANSWER.replace("{}", "{$answer}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun message(): EmailMessage =
        EmailMessage(
            id = "message-id",
            threadId = "thread-id",
            sender = "recruiter@example.com",
            subject = "Your CV",
            body = "the body of the mail",
            labelIds = listOf("INBOX"),
        )

    companion object {
        private const val DECISION_ANSWER =
            """{"model":"clef:cloud","answers":{},"usage":{"input_tokens":10,"output_tokens":1}}"""

        private val REQUIREMENTS =
            JobRequirements(
                minimumPay = PayRequirement(amount = 120_000.0, currency = "EUR"),
                maximumOfficeDays = 2,
                profile = "Senior Kotlin engineer",
            )

        private val REQUIREMENTS_WITHOUT_PAY =
            JobRequirements(
                minimumPay = null,
                maximumOfficeDays = 2,
                profile = "Senior Kotlin engineer",
            )

        private val JSON: Json = Json { ignoreUnknownKeys = true }
    }
}
