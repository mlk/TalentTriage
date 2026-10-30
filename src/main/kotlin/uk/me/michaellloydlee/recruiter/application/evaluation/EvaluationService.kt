package uk.me.michaellloydlee.recruiter.application.evaluation

import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelStateManager

/**
 * Evaluates the mails of the recruiters and puts them into the state of the
 * pipeline.
 *
 * A mail that is worth a follow up gets the label `Recruiters/WorthFollowUp`, a
 * mail that matches but does not say everything the job seeker needs to know
 * gets `Recruiters/NeedsMoreInformation` - the pay is not stated, the pay is
 * below the target or the job description does not say how many days of
 * office it demands - and a mail that does not match, or that demands more
 * days of office than the job seeker allows, gets `Recruiters/Trash` and is
 * archived. A mail the model could not evaluate gets `Recruiters/NeedsMoreInformation`
 * as well: it is not thrown away, the job seeker looks at it.
 *
 * @param evaluator the model that evaluates the job descriptions.
 * @param attachments reads the text of the attached documents.
 * @param stateManager applies the state label of the mail.
 */
class EvaluationService(
    private val evaluator: JobDescriptionEvaluator,
    private val attachments: AttachmentTextReader,
    private val stateManager: GmailLabelStateManager,
) {
    /**
     * Evaluates a mail and applies its state.
     *
     * @param message the mail with the recruiter.
     * @param requirements the requirements of the job seeker.
     * @return the evaluation of the mail.
     */
    suspend fun evaluate(
        message: EmailMessage,
        requirements: JobRequirements,
    ): JobDescriptionEvaluation {
        val evaluation =
            evaluator.evaluate(
                message = message,
                attachments = attachments.textOf(message),
                requirements = requirements,
            )
        stateManager.apply(message.id, evaluation.state)
        return evaluation
    }
}
