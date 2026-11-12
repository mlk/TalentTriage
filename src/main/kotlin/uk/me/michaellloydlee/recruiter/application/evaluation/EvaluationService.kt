package uk.me.michaellloydlee.recruiter.application.evaluation

import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements

/**
 * Evaluates the mails of the recruiters against the requirements of the job
 * seeker.
 *
 * The service reads the attachments of the mail and gives them to the
 * evaluator. It does not put the mail into a state: the state of a mail is
 * applied once the draft that asks the recruiter for the missing information
 * is written, so a mail that waits for the job seeker always carries its
 * draft.
 *
 * @param evaluator the model that evaluates the job descriptions.
 * @param attachments reads the text of the attached documents.
 */
class EvaluationService(
    private val evaluator: JobDescriptionEvaluator,
    private val attachments: AttachmentTextReader,
) {
    /**
     * Evaluates a mail.
     *
     * @param message the mail with the recruiter.
     * @param requirements the requirements of the job seeker.
     * @return the evaluation of the mail.
     */
    suspend fun evaluate(
        message: EmailMessage,
        requirements: JobRequirements,
    ): JobDescriptionEvaluation =
        evaluator.evaluate(
            message = message,
            attachments = attachments.textOf(message),
            requirements = requirements,
        )
}
