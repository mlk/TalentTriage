package uk.me.michaellloydlee.recruiter.cli

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.me.michaellloydlee.recruiter.fixtures.CHAT_ANSWER
import uk.me.michaellloydlee.recruiter.fixtures.JSON
import uk.me.michaellloydlee.recruiter.fixtures.decisionAnswer
import uk.me.michaellloydlee.recruiter.fixtures.decisionQuestions
import uk.me.michaellloydlee.recruiter.fixtures.idOf
import uk.me.michaellloydlee.recruiter.fixtures.jsonHeaders
import uk.me.michaellloydlee.recruiter.fixtures.message
import uk.me.michaellloydlee.recruiter.fixtures.messageList
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlin.io.path.writeText

internal val NOW: Instant = Instant.parse("2026-10-30T19:00:00Z")

internal val LAST_RUN: Instant = Instant.parse("2026-10-30T18:00:00Z")

internal fun tempDir(
    root: Path,
    prefix: String,
): Path = Files.createTempDirectory(root, prefix)

internal val MESSAGE_LIST: String = messageList("message-id")

internal val MESSAGE_LIST_TWO: String = messageList("first", "second")

internal fun engine(
    webhookStatus: HttpStatusCode = HttpStatusCode.OK,
    officeDays: String = "matches",
    jdMatches: String = "matches",
    messageList: String = MESSAGE_LIST,
    listing: Boolean = false,
    recruiterAnswer: String = "from a recruiter",
): MockEngine =
    MockEngine { request ->
        if (request.url.encodedPath == "/hook") {
            respond(
                content = "",
                status = webhookStatus,
                headers = jsonHeaders(),
            )
        } else {
            respond(
                content = contentFor(request, officeDays, jdMatches, messageList, listing, recruiterAnswer),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
    }

internal fun contentFor(
    request: HttpRequestData,
    officeDays: String = "matches",
    jdMatches: String = "matches",
    messageList: String = MESSAGE_LIST,
    listing: Boolean = false,
    recruiterAnswer: String = "from a recruiter",
): String =
    when {
        request.url.encodedPath.endsWith("/v1/systemone") -> {
            decisionAnswerFor(request, officeDays, jdMatches, recruiterAnswer)
        }

        request.url.encodedPath.endsWith("/api/chat") -> {
            CHAT_ANSWER
        }

        request.url.encodedPath.endsWith("/users/me/messages") -> {
            messageList
        }

        request.url.encodedPath.endsWith("/users/me/drafts") -> {
            """{"id":"draft-id"}"""
        }

        request.url.encodedPath.endsWith("/users/me/labels") -> {
            if (request.method.value == "GET") {
                LABEL_LIST
            } else {
                val name =
                    Json
                        .parseToJsonElement((request.body as TextContent).text)
                        .jsonObject["name"]!!
                        .jsonPrimitive
                        .content
                """{"id":"Label_${name.substringAfterLast('/').lowercase()}","name":"$name"}"""
            }
        }

        else -> {
            message(idOf(request), listing = listing)
        }
    }

/**
 * The answer of the decision model: the recruiter question of the screening
 * and the criteria of the evaluation are answered differently.
 */
internal fun decisionAnswerFor(
    request: HttpRequestData,
    officeDays: String = "matches",
    jdMatches: String = "matches",
    recruiterAnswer: String = "from a recruiter",
): String {
    val questions = decisionQuestions((request.body as TextContent).text)
    return decisionAnswer(questions) { name ->
        when (name) {
            "recruiter" -> recruiterAnswer to 0.9
            "jd_matches" -> jdMatches to 0.9
            "office_days" -> officeDays to 0.9
            else -> "does not match" to 0.9
        }
    }
}

internal fun mockClient(engine: MockEngine): HttpClient =
    HttpClient(engine) {
        install(HttpTimeout)
        install(ContentNegotiation) { json(JSON) }
    }

internal fun cli(
    sink: StringBuilder,
    clock: Clock = Clock.systemUTC(),
    httpClientFactory: () -> HttpClient = { HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }) },
): TriageCli = TriageCli(StructuredLogger(sink), httpClientFactory, clock, webhookClientFactory = httpClientFactory)

internal fun credentialsFile(root: Path): Path {
    val file = tempDir(root, "credentials").resolve("credentials.json")
    file.writeText("""{"client_id":"client-id","client_secret":"client-secret"}""")
    return file
}

internal fun rulesFile(
    root: Path,
    content: String,
): Path {
    val file = tempDir(root, "rules").resolve("rules.json")
    file.writeText(content)
    return file
}

internal fun missingRulesFile(root: Path): Path = tempDir(root, "rules").resolve("rules.json")

internal fun defaultRulesFile(root: Path): Path {
    val file = tempDir(root, "rules").resolve("rules.json")
    file.writeText("{}")
    return file
}

internal fun requirementsFile(root: Path): Path {
    val file = tempDir(root, "requirements").resolve("requirements.json")
    file.writeText(
        """{"maximum_office_days": 2, "profile": "Senior Kotlin engineer"}""",
    )
    return file
}

// The token is checked against the system clock of the machine the test runs
// on, not the injected test clock, so the default expiry has to be relative
// to the real now.
internal fun tokenFile(
    root: Path,
    expiresAt: Instant = Instant.now().plusSeconds(3600),
): Path {
    val file = tempDir(root, "token").resolve("token.json")
    file.writeText(
        """{"access_token":"access-token","refresh_token":"refresh-token","expires_at":"$expiresAt"}""",
    )
    return file
}

internal fun records(sink: StringBuilder): List<LogRecord> =
    sink
        .toString()
        .trim()
        .lines()
        .filter { it.isNotBlank() }
        .map { Json.decodeFromString<LogRecord>(it) }

internal const val LABEL_LIST: String =
    """{"labels":[{"id":"Label_joblisting","name":"Recruiters/JobListing"}]}"""
