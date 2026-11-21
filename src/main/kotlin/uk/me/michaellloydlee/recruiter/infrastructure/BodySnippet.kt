package uk.me.michaellloydlee.recruiter.infrastructure

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

private const val MAX_SNIPPET_LENGTH: Int = 500
