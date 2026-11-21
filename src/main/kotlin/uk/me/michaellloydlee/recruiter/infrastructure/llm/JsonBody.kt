package uk.me.michaellloydlee.recruiter.infrastructure.llm

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The answer of an endpoint that answers with a single JSON object.
 *
 * The content type is checked before the body is read: an answer that is not
 * JSON - a streamed answer, an HTML error page - is rejected without draining
 * the body. An answer that is not the JSON the client reads becomes an
 * [OllamaException] that names the answer.
 *
 * @param json the parser of the answer.
 * @param deserializer the parser of the answer.
 * @param response the answer of the API.
 * @return the answer of the API.
 */
internal suspend fun <T> decodeJsonBody(
    json: Json,
    deserializer: DeserializationStrategy<T>,
    response: HttpResponse,
): T {
    val contentType = response.contentType()
    if (!contentType.isJson()) {
        // The API streamed the answer although the request asked for the
        // whole one, the client cannot read a stream.
        throw OllamaException(
            response.status.value,
            "The API answered with $contentType, the client reads a single JSON object",
        )
    }
    val body = response.bodyAsText()
    return try {
        json.decodeFromString(deserializer, body)
    } catch (error: SerializationException) {
        throw OllamaException.unreadableBody(response.status.value, body, error)
    }
}

/**
 * Whether the content type of an answer carries a JSON document. A missing
 * content type is decoded: whether it is JSON is decided by the parser.
 */
internal fun ContentType?.isJson(): Boolean =
    this == null ||
        (
            contentType.equals("application", ignoreCase = true) &&
                (
                    contentSubtype.equals("json", ignoreCase = true) ||
                        contentSubtype.endsWith("+json", ignoreCase = true)
                )
        )
