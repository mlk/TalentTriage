package uk.me.michaellloydlee.recruiter.infrastructure

import io.ktor.http.ContentType

/**
 * The beginning of a body that could not be read.
 *
 * A body can be megabytes long, an exception that names the answer keeps the
 * first characters of it and no more.
 *
 * @param body the body of the answer.
 * @param maxLength how many characters of the body are kept.
 * @return the beginning of the body, the whole body when it is short enough.
 */
internal fun bodySnippet(
    body: String,
    maxLength: Int = MAX_SNIPPET_LENGTH,
): String = if (body.length <= maxLength) body else body.take(maxLength) + "..."

/**
 * Whether the content type of an answer carries a JSON document.
 *
 * @param contentType the content type of the answer, `null` when the answer
 *   does not say what it is.
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

private const val MAX_SNIPPET_LENGTH: Int = 500
