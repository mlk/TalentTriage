package uk.me.michaellloydlee.recruiter.fixtures

import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.Base64

/**
 * The payload fixtures the tests of the pipeline share: the messages of the
 * mailbox, the JSON payloads and the headers of the answers. The fixtures of
 * the single packages build on these, they are not copied around.
 */
internal val JSON: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

internal const val CHAT_ANSWER: String =
    """{"message":{"role":"assistant","content":"the reply of the model"},"done":true}"""

internal fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/**
 * The answer of the decision API of Ollama: the request carries the questions
 * as an object keyed by name, the answer carries one `choice` per question.
 *
 * @param questions the questions of the request, keyed by name.
 * @param answerFor the answer of each question as a choice and a confidence.
 */
internal fun decisionAnswer(
    questions: JsonObject,
    answerFor: (String) -> Pair<String, Double>,
): String {
    val answer =
        questions.keys.joinToString(",") { name ->
            val (choice, confidence) = answerFor(name)
            """"$name":{"type":"choice","choice":"$choice","confidence":$confidence}"""
        }
    return """{"model":"clef:cloud","answers":{$answer},"usage":{"input_tokens":10,"output_tokens":1}}"""
}

internal fun decisionQuestions(body: String): JsonObject =
    Json.parseToJsonElement(body).jsonObject["questions"]!!.jsonObject

internal fun messageList(vararg ids: String): String =
    ids.joinToString(prefix = """{"messages":[""", postfix = "]}", separator = ",") { id ->
        """{"id":"$id","threadId":"thread-$id"}"""
    }

internal fun message(
    id: String,
    listing: Boolean = false,
    labels: List<String> = listOf("INBOX"),
): String {
    val sender =
        if (listing) {
            "LinkedIn Job Alerts <jobalerts-noreply@linkedin.com>"
        } else {
            "Jane Recruiter <recruiter@example.com>"
        }
    return """
        {
          "id": "$id",
          "threadId": "thread-$id",
          "labelIds": [${labels.joinToString(",") { label -> "\"$label\"" }}],
          "payload": {
            "mimeType": "text/plain",
            "headers": [
              {"name": "From", "value": "$sender"},
              {"name": "Subject", "value": "Your CV"}
            ],
            "body": {"data": "${bodyOf(id)}"}
          }
        }
        """.trimIndent()
}

/**
 * The body of a message, it names the message it belongs to, so a test that
 * fetches two messages can tell them apart.
 */
internal fun bodyOf(id: String): String =
    Base64.getUrlEncoder().encodeToString("Looking for a developer $id".toByteArray())

/**
 * The id of the message the request asks for.
 */
internal fun idOf(request: HttpRequestData): String =
    request.url.encodedPath
        .substringAfterLast("/users/me/messages/")
        .substringBefore("/")
