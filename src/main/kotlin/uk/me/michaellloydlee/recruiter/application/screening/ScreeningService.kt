package uk.me.michaellloydlee.recruiter.application.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
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
 * local model. A message that is not a recruiter mail gets the label
 * `Recruiters/FilteredOut` and stays in the inbox - the label is what makes
 * the next run of the pipeline skip it.
 *
 * A message the local model could not answer about is not filtered out: it
 * stays in the inbox and is looked at again by the next run.
 *
 * @param heuristics the rules of the first stage.
 * @param classifier the local model of the second stage.
 * @param stateManager applies the state label of a filtered out message.
 */
class ScreeningService(
    private val heuristics: RecruiterHeuristics,
    private val classifier: RecruiterVerificationClassifier,
    private val stateManager: GmailLabelStateManager,
) {
    /**
     * Screens a message.
     *
     * @param message the message to screen.
     * @return the decision and the stage that made it.
     */
    suspend fun screen(message: EmailMessage): ScreeningOutcome {
        if (!heuristics.isCandidate(message)) {
            val reason = "The message matches none of the recruiter rules"
            stateManager.apply(message.id, RecruiterLabel.FILTERED_OUT)
            return ScreeningOutcome(
                decision = ScreeningDecision.NOT_RECRUITER,
                stage = ScreeningStage.HEURISTICS,
                reason = reason,
            )
        }
        return when (val answer = classifier.classify(message)) {
            LocalClassification.Recruiter -> {
                ScreeningOutcome(
                    decision = ScreeningDecision.RECRUITER,
                    stage = ScreeningStage.LOCAL_MODEL,
                    reason = "The local model sees a recruiter mail in it",
                )
            }

            LocalClassification.NotRecruiter -> {
                val reason = "The local model does not see a recruiter mail in it"
                stateManager.apply(message.id, RecruiterLabel.FILTERED_OUT)
                ScreeningOutcome(
                    decision = ScreeningDecision.NOT_RECRUITER,
                    stage = ScreeningStage.LOCAL_MODEL,
                    reason = reason,
                )
            }

            is LocalClassification.Unavailable -> {
                ScreeningOutcome(
                    decision = ScreeningDecision.UNKNOWN,
                    stage = ScreeningStage.LOCAL_MODEL,
                    reason = answer.reason,
                )
            }
        }
    }
}
