package uk.me.michaellloydlee.recruiter.application.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import uk.me.michaellloydlee.recruiter.domain.screening.JobListingHeuristics
import uk.me.michaellloydlee.recruiter.domain.screening.RecruiterHeuristics
import uk.me.michaellloydlee.recruiter.domain.screening.ScreeningDecision
import uk.me.michaellloydlee.recruiter.domain.screening.ScreeningOutcome
import uk.me.michaellloydlee.recruiter.domain.screening.ScreeningStage
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelStateManager

/**
 * Screens the messages of the inbox and filters out the ones that are not
 * recruiter mails.
 *
 * The screening has two stages: the keyword and sender rules of the
 * [heuristics] decide first, only the messages that pass them are shown to the
 * model. A message that is not a recruiter mail gets the label
 * `Recruiters/FilteredOut` and stays in the inbox - the label is what makes
 * the next run of the pipeline skip it.
 *
 * The rules of the job listing come before the rules of the recruiter: a
 * listing is not one job, so it is not evaluated, and the rules of the
 * recruiter would send it to the evaluation.
 *
 * A job listing is an alert of a job board, it offers many jobs at once. The
 * rules of the [jobListings] decide it without a model, and the model is the
 * fallback for the messages the rules do not catch. A listing gets the label
 * `Recruiters/JobListing` and stays in the inbox, it is not evaluated: it is
 * not one job.
 *
 * A message the model could not answer about is not filtered out: it
 * stays in the inbox and is looked at again by the next run.
 *
 * @param heuristics the rules of the first stage.
 * @param jobListings the rules that tell a job listing apart.
 * @param verifier the model of the second stage.
 * @param stateManager applies the state label of a filtered out message.
 */
class ScreeningService(
    private val heuristics: RecruiterHeuristics,
    private val jobListings: JobListingHeuristics = JobListingHeuristics(),
    private val verifier: RecruiterVerifier,
    private val stateManager: GmailLabelStateManager,
) {
    /**
     * Screens a message.
     *
     * @param message the message to screen.
     * @return the decision and the stage that made it.
     */
    suspend fun screen(message: EmailMessage): ScreeningOutcome {
        if (jobListings.isJobListing(message)) {
            val reason = "The message looks like a job listing"
            stateManager.apply(message.id, RecruiterLabel.JOB_LISTING)
            return ScreeningOutcome(
                decision = ScreeningDecision.JOB_LISTING,
                stage = ScreeningStage.HEURISTICS,
                reason = reason,
            )
        }
        if (!heuristics.isCandidate(message)) {
            val reason = "The message matches none of the recruiter rules"
            stateManager.apply(message.id, RecruiterLabel.FILTERED_OUT)
            return ScreeningOutcome(
                decision = ScreeningDecision.NOT_RECRUITER,
                stage = ScreeningStage.HEURISTICS,
                reason = reason,
            )
        }
        return when (val answer = verifier.verify(message)) {
            RecruiterVerification.Recruiter -> {
                ScreeningOutcome(
                    decision = ScreeningDecision.RECRUITER,
                    stage = ScreeningStage.LOCAL_MODEL,
                    reason = "The model sees a recruiter mail in it",
                )
            }

            RecruiterVerification.JobListing -> {
                val reason = "The model sees a job listing in it"
                stateManager.apply(message.id, RecruiterLabel.JOB_LISTING)
                ScreeningOutcome(
                    decision = ScreeningDecision.JOB_LISTING,
                    stage = ScreeningStage.LOCAL_MODEL,
                    reason = reason,
                )
            }

            is RecruiterVerification.NotRecruiter -> {
                stateManager.apply(message.id, RecruiterLabel.FILTERED_OUT)
                ScreeningOutcome(
                    decision = ScreeningDecision.NOT_RECRUITER,
                    stage = ScreeningStage.LOCAL_MODEL,
                    reason = answer.reason,
                )
            }

            is RecruiterVerification.Unknown -> {
                ScreeningOutcome(
                    decision = ScreeningDecision.UNKNOWN,
                    stage = ScreeningStage.LOCAL_MODEL,
                    reason = answer.reason,
                )
            }
        }
    }
}
