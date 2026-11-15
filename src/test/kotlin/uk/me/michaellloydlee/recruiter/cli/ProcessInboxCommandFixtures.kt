package uk.me.michaellloydlee.recruiter.cli

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import uk.me.michaellloydlee.recruiter.infrastructure.logging.LogRecord
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

internal val NOW: Instant = Instant.parse("2026-10-30T19:00:00Z")

internal val LAST_RUN: Instant = Instant.parse("2026-10-30T18:00:00Z")

// The test suite runs sequentially: no JUnit parallel execution is configured
// and Gradle uses a single fork. The registry is used from one thread at a
// time; the lock is cheap insurance, not a correctness requirement.
private val tempDirs = mutableListOf<Path>()

internal fun tempDir(prefix: String): Path =
    createTempDirectory(prefix).also { dir ->
        synchronized(tempDirs) { tempDirs.add(dir) }
    }

internal fun cleanupTempDirs() {
    synchronized(tempDirs) {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
    }
}

internal fun engine(webhookStatus: HttpStatusCode = HttpStatusCode.OK): MockEngine =
    MockEngine { request ->
        if (request.url.encodedPath == "/hook") {
            respond(
                content = "",
                status = webhookStatus,
                headers = jsonHeaders(),
            )
        } else {
            respond(
                content = contentFor(request),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
    }

internal fun contentFor(request: HttpRequestData): String =
    when {
        request.url.encodedPath.endsWith("/v1/systemone") -> {
            decisionAnswer(request)
        }

        request.url.encodedPath.endsWith("/api/chat") -> {
            CHAT_ANSWER
        }

        request.url.encodedPath.endsWith("/users/me/messages") -> {
            MESSAGE_LIST
        }

        request.url.encodedPath.endsWith("/users/me/labels") -> {
            if (request.method.value == "GET") LABEL_LIST else LABEL
        }

        else -> {
            MESSAGE
        }
    }

/**
 * The answer of the decision model: the recruiter question of the screening
 * and the criteria of the evaluation are answered differently.
 */
internal fun decisionAnswer(request: HttpRequestData): String {
    val body = (request.body as TextContent).text
    val questions = Json.parseToJsonElement(body).jsonObject["questions"]!!.jsonObject
    val answer =
        questions.keys.joinToString(",") { name ->
            val choice =
                when (name) {
                    "recruiter" -> "from a recruiter"
                    "jd_matches", "office_days" -> "matches"
                    else -> "does not match"
                }
            """"$name":{"type":"choice","choice":"$choice","confidence":0.9}"""
        }
    return """{"model":"clef:cloud","answers":{$answer},"usage":{"input_tokens":10,"output_tokens":1}}"""
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
): TriageCli = TriageCli(StructuredLogger(sink), httpClientFactory, clock)

internal fun credentialsFile(): Path {
    val file = tempDir("credentials").resolve("credentials.json")
    file.writeText("""{"client_id":"client-id","client_secret":"client-secret"}""")
    return file
}

internal fun rulesFile(content: String): Path {
    val file = tempDir("rules").resolve("rules.json")
    file.writeText(content)
    return file
}

internal fun missingRulesFile(): Path = tempDir("rules").resolve("rules.json")

internal fun emptyRulesFile(): Path {
    val file = tempDir("rules").resolve("rules.json")
    file.writeText("{}")
    return file
}

internal fun requirementsFile(): Path {
    val file = tempDir("requirements").resolve("requirements.json")
    file.writeText(
        """{"maximum_office_days": 2, "profile": "Senior Kotlin engineer"}""",
    )
    return file
}

internal fun tokenFile(): Path {
    val file = tempDir("token").resolve("token.json")
    val expiry = Instant.now().plusSeconds(3600)
    file.writeText(
        """{"access_token":"access-token","refresh_token":"refresh-token","expires_at":"$expiry"}""",
    )
    return file
}

internal fun records(sink: StringBuilder): List<LogRecord> =
    sink
        .toString()
        .trim()
        .lines()
        .map { Json.decodeFromString<LogRecord>(it) }

internal fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

internal val JSON: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

internal const val CHAT_ANSWER: String =
    """{"message":{"role":"assistant","content":"no"},"done":true}"""

internal const val MESSAGE_LIST: String =
    """{"messages":[{"id":"message-id","threadId":"thread-id"}]}"""

internal const val LABEL_LIST: String =
    """{"labels":[{"id":"Label_joblisting","name":"Recruiters/JobListing"}]}"""

internal const val LABEL: String =
    """{"id":"Label_joblisting","name":"Recruiters/JobListing"}"""

internal val MESSAGE: String =
    """
    {
      "id": "message-id",
      "threadId": "thread-id",
      "payload": {
        "mimeType": "text/plain",
        "headers": [
          {"name": "From", "value": "recruiter@example.com"},
          {"name": "Subject", "value": "Your CV"}
        ],
        "body": {"data": "WWUgbG9va2luZyBmb3IgYSBkZXZlbG9wZXI="}
      }
    }
    """.trimIndent()
