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
import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaChatClient
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatModelEvaluatorTest {
    @Test
    fun readsTheAnswerOfTheModel() =
        runTest {
            val engine =
                engine("""{"pay_matches":true,"jd_matches":true,"office_days_matches":true,"pay_missing":false}""")

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val evaluated = assertIs<JobDescriptionEvaluation.Evaluated>(evaluation)
            assertEquals(true, evaluated.payMatches)
            assertEquals(true, evaluated.jdMatches)
            assertEquals(true, evaluated.officeDaysMatch)
            assertEquals(false, evaluated.payMissing)
        }

    @Test
    fun readsTheAnswerOutOfASentence() =
        runTest {
            val engine = engine("Here you are: {\"jd_matches\":true,\"office_days_matches\":true} it is a good job")

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val evaluated = assertIs<JobDescriptionEvaluation.Evaluated>(evaluation)
            assertEquals(true, evaluated.jdMatches)
        }

    @Test
    fun asksTheModelWithTheRequirements() =
        runTest {
            val engine = engine("{}")

            evaluator(engine).evaluate(message(), "the job description", REQUIREMENTS)

            val body = (engine.requestHistory.single().body as TextContent).text
            assertTrue(body.contains("Senior Kotlin engineer"))
            assertTrue(body.contains("120000 EUR"))
            assertTrue(body.contains("at most 2 days"))
        }

    @Test
    fun anAnswerThatIsNoJsonLeavesTheMailAlone() =
        runTest {
            val engine = engine("I cannot answer that")

            val evaluation = evaluator(engine).evaluate(message(), "", REQUIREMENTS)

            val unknown = assertIs<JobDescriptionEvaluation.Unknown>(evaluation)
            assertTrue(unknown.reason.contains("JSON"))
        }

    @Test
    fun anAnswerWithoutEveryFieldLeavesTheMailAlone() =
        runTest {
            val engine = engine("""{"jd_matches":true}""")

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

    private fun evaluator(engine: MockEngine): ChatModelEvaluator =
        ChatModelEvaluator(
            client =
                OllamaChatClient(
                    httpClient =
                        HttpClient(engine) {
                            install(HttpTimeout)
                            install(ContentNegotiation) { json(JSON) }
                        },
                ),
        )

    private fun engine(answer: String): MockEngine =
        MockEngine {
            respond(
                content = """{"message":{"role":"assistant","content":${quote(answer)}},"done":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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
        private val REQUIREMENTS =
            JobRequirements(
                minimumPay = PayRequirement(amount = 120_000.0, currency = "EUR"),
                maximumOfficeDays = 2,
                profile = "Senior Kotlin engineer",
            )

        private val JSON: Json = Json { ignoreUnknownKeys = true }
    }
}
