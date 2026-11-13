package uk.me.michaellloydlee.recruiter.application

import uk.me.michaellloydlee.recruiter.application.draft.DraftOutcome
import uk.me.michaellloydlee.recruiter.application.draft.DraftWriter
import uk.me.michaellloydlee.recruiter.application.evaluation.EvaluationService
import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.evaluation.EvaluationState
import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.gmail.RunTarget
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.screening.ScreeningDecision
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailLabelStateManager
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.llm.ModelUsageCounter
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger
import java.time.Clock
import java.time.Duration

/**
 * Runs one pass of the pipeline over the messages of the inbox.
 *
 * The messages that were not processed yet are fetched and screened. The
 * recruiter mails are evaluated against the requirements of the job seeker and
 * end in one of the states of the pipeline. A mail that matches but does not
 * say everything the job seeker needs to know gets a draft that asks the
 * recruiter about it.
 *
 * The draft is written before the state of the mail is applied: a mail that
 * waits for the job seeker always carries the draft that asks the recruiter,
 * or it keeps no state at all and the next run looks at it again.
 *
 * The run looks at what its target says: the messages of the inbox that
 * arrived after a moment, or one message the job seeker asks for by its id.
 *
 * @param messages the message endpoints of the Gmail API.
 * @param screening the screening of the messages.
 * @param evaluation the evaluation of the job descriptions.
 * @param drafts writes the draft that asks the recruiter for the missing
 *   information.
 * @param requirements the requirements of the job seeker.
 * @param stateManager keeps the state of the messages.
 * @param usageCounter counts what the calls to the models took.
 * @param clock the clock of the run, it says how long the run took.
 * @param logger sink for the log of the run.
 */
class ProcessInboxService(
    private val messages: GmailMessagesApi,
    private val screening: ScreeningService,
    private val evaluation: EvaluationService,
    private val drafts: DraftWriter,
    private val requirements: JobRequirements,
    private val stateManager: GmailLabelStateManager,
    private val usageCounter: ModelUsageCounter = ModelUsageCounter(),
    private val clock: Clock = Clock.systemUTC(),
    private val logger: StructuredLogger,
) {
    /**
     * Runs one pass of the pipeline.
     *
     * @param target what the run looks at.
     * @return what the run did.
     */
    suspend fun run(target: RunTarget): ProcessInboxSummary {
        val startedAt = clock.instant()
        val pending = messagesForTarget(target)
        val counters = Counters()
        pending.forEach { message ->
            screen(message, counters)
        }
        return ProcessInboxSummary(
            target = target,
            screened = pending.size,
            recruiters = counters.recruiters,
            filteredOut = counters.filteredOut,
            unknown = counters.unknown,
            jobListings = counters.jobListings,
            worthFollowUp = counters.worthFollowUp,
            needsMoreInformation = counters.needsMoreInformation,
            trashed = counters.trashed,
            drafts = counters.drafts,
            failed = counters.failed,
            models = usageCounter.usage(),
            durationMillis = Duration.between(startedAt, clock.instant()).toMillis(),
        )
    }

    /**
     * The messages the run looks at.
     *
     * A run of the inbox looks at the messages of the window that were not
     * processed yet. A run of a message looks at that message only: the
     * message is looked at as if it were pending, the state of an earlier run
     * is removed before it is screened.
     *
     * @param target what the run looks at.
     * @return the messages of the run.
     */
    private suspend fun messagesForTarget(target: RunTarget): List<EmailMessage> =
        when (target) {
            is RunTarget.Window -> {
                messages.listUnprocessed(target.since).map { messages.getMessage(it.id) }
            }

            is RunTarget.Message -> {
                val message = messages.getMessage(target.id)
                stateManager.clear(message)
                listOf(message)
            }
        }

    private suspend fun screen(
        message: EmailMessage,
        counters: Counters,
    ) {
        val outcome = screening.screen(message)
        logger.info(
            event = EVENT_SCREENED,
            message = "A message of the inbox was screened",
            attributes =
                mapOf(
                    "id" to message.id,
                    "decision" to outcome.decision.name,
                    "stage" to outcome.stage.name,
                    "reason" to outcome.reason,
                ).let { attrs ->
                    if (outcome.decision == ScreeningDecision.UNKNOWN) {
                        attrs + ("subject" to message.subject)
                    } else {
                        attrs
                    }
                },
        )
        when (outcome.decision) {
            ScreeningDecision.RECRUITER -> {
                counters.recruiters++
                evaluate(message, counters)
            }

            ScreeningDecision.JOB_LISTING -> {
                counters.jobListings++
            }

            ScreeningDecision.NOT_RECRUITER -> {
                counters.filteredOut++
            }

            ScreeningDecision.UNKNOWN -> {
                counters.unknown++
            }
        }
    }

    private suspend fun evaluate(
        message: EmailMessage,
        counters: Counters,
    ) {
        val evaluation =
            evaluation.evaluate(
                message = message,
                requirements = requirements,
            )
        logger.info(
            event = EVENT_EVALUATED,
            message = "A job description of the inbox was evaluated",
            attributes =
                mapOf(
                    "id" to message.id,
                    "state" to evaluation.state.name,
                    "reason" to reasonOf(evaluation),
                ),
        )
        // An evaluation leads to one of three states. A job listing is a
        // decision of the screening, it is not evaluated.
        when (evaluation.state) {
            EvaluationState.WORTH_FOLLOW_UP -> {
                counters.worthFollowUp++
                stateManager.apply(message.id, evaluation.state)
            }

            EvaluationState.NEEDS_MORE_INFORMATION -> {
                needsMoreInformation(message, evaluation, counters)
            }

            EvaluationState.TRASH -> {
                counters.trashed++
                stateManager.apply(message.id, evaluation.state)
            }
        }
    }

    /**
     * Writes the draft that asks the recruiter for the missing information and
     * puts the mail into its state.
     *
     * The draft is written before the state label is applied: a mail that
     * waits for the job seeker always carries the draft that asks the
     * recruiter. A draft that could not be written is a failure of the mail:
     * the mail keeps no label and the next run looks at it again.
     *
     * A mail the model could not evaluate gets no draft: the model does not
     * know what is missing. A mail whose only gap is a criterion the model was
     * not sure about gets no draft either: the recruiter cannot be asked
     * about what the model does not know.
     *
     * @param message the mail of the recruiter.
     * @param evaluation the evaluation of the job description.
     * @param counters the counters of the run.
     */
    private suspend fun needsMoreInformation(
        message: EmailMessage,
        evaluation: JobDescriptionEvaluation,
        counters: Counters,
    ) {
        val missing =
            when (evaluation) {
                is JobDescriptionEvaluation.Evaluated -> evaluation.missingInformation

                // The model could not evaluate the job description: it does
                // not know what the mail is missing, the recruiter is not
                // asked about it.
                is JobDescriptionEvaluation.Unknown -> null
            }
        if (missing == null || (!missing.asksAboutPay && !missing.asksAboutOfficeDays)) {
            // The recruiter is not asked about anything: the mail waits for
            // the job seeker.
            counters.needsMoreInformation++
            stateManager.apply(message.id, evaluation.state)
            return
        }
        when (val outcome = drafts.write(message, missing, requirements)) {
            is DraftOutcome.Written -> {
                counters.drafts++
                counters.needsMoreInformation++
                logger.info(
                    event = EVENT_DRAFT_WRITTEN,
                    message = "A draft that asks the recruiter for the missing information was written",
                    attributes =
                        mapOf(
                            "id" to message.id,
                            "draft" to outcome.draftId,
                            "reason" to reasonOf(evaluation),
                        ),
                )
                // A label that cannot be applied leaves the mail with its draft
                // and without a label: the next run writes a second draft for
                // the same mail.
                stateManager.apply(message.id, evaluation.state)
            }

            is DraftOutcome.Failed -> {
                // The mail keeps no label: the next run looks at it again and
                // writes the draft.
                counters.failed++
                logger.warn(
                    event = EVENT_DRAFT_FAILED,
                    message = "The draft that asks the recruiter for the missing information was not written",
                    attributes =
                        mapOf(
                            "id" to message.id,
                            "reason" to outcome.reason,
                        ),
                )
            }
        }
    }

    /**
     * The reason of the log of an evaluation.
     *
     * @param evaluation the evaluation of the job description.
     * @return the reason of the log.
     */
    private fun reasonOf(evaluation: JobDescriptionEvaluation): String =
        when (evaluation) {
            is JobDescriptionEvaluation.Evaluated -> {
                evaluation.reason
            }

            is JobDescriptionEvaluation.Unknown -> {
                "The model could not evaluate the job description: ${evaluation.reason}"
            }
        }

    /**
     * The counters of a run.
     */
    private class Counters {
        var recruiters: Int = 0
        var filteredOut: Int = 0
        var unknown: Int = 0
        var jobListings: Int = 0
        var worthFollowUp: Int = 0
        var needsMoreInformation: Int = 0
        var trashed: Int = 0
        var drafts: Int = 0
        var failed: Int = 0
    }

    companion object {
        /** The event of the log of a screened message. */
        const val EVENT_SCREENED: String = "process-inbox.screened"

        /** The event of the log of an evaluated job description. */
        const val EVENT_EVALUATED: String = "process-inbox.evaluated"

        /** The event of the log of a draft that was written. */
        const val EVENT_DRAFT_WRITTEN: String = "process-inbox.draft_written"

        /** The event of the log of a draft that could not be written. */
        const val EVENT_DRAFT_FAILED: String = "process-inbox.draft_failed"
    }
}
