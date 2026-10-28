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
 * mail that matches but says nothing about the pay gets
 * `Recruiters/NeedsPayCheck`, a mail that matches but does not say how many
 * days of office it demands gets `Recruiters/NeedsWorkFromHomeCheck` and a
 * mail that does not match gets `Recruiters/Trash` and is archived. A mail the
 * model could not decide about stays in the inbox and is looked at again by the
 * next run.
 *
 * @param evaluator the model that evaluates the job descriptions.
 * @param stateManager applies the state label of the mail.
 */
class EvaluationService(
    private val evaluator: JobDescriptionEvaluator,
    private val stateManager: GmailLabelStateManager,
) {
    /**
     * Evaluates a mail and applies its state.
     *
     * @param message the mail with the recruiter.
     * @param attachments the text of the attached documents.
     * @param requirements the requirements of the job seeker.
     * @return the evaluation of the mail.
     */
    suspend fun evaluate(
        message: EmailMessage,
        attachments: String,
        requirements: JobRequirements,
    ): JobDescriptionEvaluation {
        val evaluation = evaluator.evaluate(message, attachments, requirements)
        if (evaluation is JobDescriptionEvaluation.Evaluated) {
            stateManager.apply(message.id, evaluation.state)
        }
        return evaluation
    }
}
