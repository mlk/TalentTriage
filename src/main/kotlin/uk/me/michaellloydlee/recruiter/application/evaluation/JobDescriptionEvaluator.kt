package uk.me.michaellloydlee.recruiter.application.evaluation

import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements

/**
 * Evaluates a job description against the requirements of the job seeker.
 *
 * The pipeline has two implementations: the decision model of Ollama, which
 * answers a typed question, and a chat model, which answers in words. The
 * command line chooses between them.
 */
interface JobDescriptionEvaluator {
    /**
     * Evaluates a message with its attachments.
     *
     * @param message the mail with the recruiter.
     * @param attachments the text of the attached documents, empty when the
     *   mail has none.
     * @param requirements the requirements of the job seeker.
     * @return the evaluation, an unknown evaluation leaves the mail alone.
     */
    suspend fun evaluate(
        message: EmailMessage,
        attachments: String,
        requirements: JobRequirements,
    ): JobDescriptionEvaluation
}

/**
 * The text the model is asked about: the mail and the documents that came
 * with it.
 *
 * @param message the mail with the recruiter.
 * @param attachments the text of the attached documents.
 */
internal fun evaluationText(
    message: EmailMessage,
    attachments: String,
): String =
    buildString {
        appendLine("Subject: ${message.subject}")
        appendLine()
        appendLine("Body:")
        appendLine(message.body)
        if (attachments.isNotBlank()) {
            appendLine()
            appendLine("Attachments:")
            append(attachments)
        }
    }.trim()
