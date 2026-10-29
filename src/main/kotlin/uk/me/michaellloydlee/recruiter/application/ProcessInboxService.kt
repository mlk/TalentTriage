package uk.me.michaellloydlee.recruiter.application

import uk.me.michaellloydlee.recruiter.application.evaluation.AttachmentTextReader
import uk.me.michaellloydlee.recruiter.application.evaluation.EvaluationService
import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.evaluation.JobDescriptionEvaluation
import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage
import uk.me.michaellloydlee.recruiter.domain.gmail.MessageWindow
import uk.me.michaellloydlee.recruiter.domain.gmail.RecruiterLabel
import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements
import uk.me.michaellloydlee.recruiter.domain.screening.ScreeningDecision
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger

/**
 * Runs one pass of the pipeline over the messages of the inbox.
 *
 * The messages that were not processed yet are fetched and screened. The
 * recruiter mails are evaluated against the requirements of the job seeker and
 * end in one of the states of the pipeline.
 *
 * @param messages the message endpoints of the Gmail API.
 * @param window how far back the run looks for messages.
 * @param screening the screening of the messages.
 * @param evaluation the evaluation of the job descriptions.
 * @param attachments reads the text of the attached documents.
 * @param requirements the requirements of the job seeker.
 * @param logger sink for the log of the run.
 */
class ProcessInboxService(
    private val messages: GmailMessagesApi,
    private val window: MessageWindow = MessageWindow.LAST_DAY,
    private val screening: ScreeningService,
    private val evaluation: EvaluationService,
    private val attachments: AttachmentTextReader,
    private val requirements: JobRequirements,
    private val logger: StructuredLogger,
) {
    /**
     * Runs one pass of the pipeline.
     *
     * @return what the run did.
     */
    suspend fun run(): ProcessInboxSummary {
        val pending = messages.listUnprocessed(window)
        val counters = Counters()
        pending.forEach { summary ->
            screen(messages.getMessage(summary.id), counters)
        }
        return ProcessInboxSummary(
            screened = pending.size,
            recruiters = counters.recruiters,
            filteredOut = counters.filteredOut,
            unknown = counters.unknown,
            jobListings = counters.jobListings,
            worthFollowUp = counters.worthFollowUp,
            needsMoreInformation = counters.needsMoreInformation,
            trashed = counters.trashed,
            window = window,
        )
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
                ),
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
                attachments = attachments.textOf(message),
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
        // An evaluation leads to one of three states. The screening decides
        // `FilteredOut` and `JobListing`, the two branches below are there
        // for the compiler.
        when (evaluation.state) {
            RecruiterLabel.WORTH_FOLLOW_UP -> counters.worthFollowUp++
            RecruiterLabel.NEEDS_MORE_INFORMATION -> counters.needsMoreInformation++
            RecruiterLabel.TRASH -> counters.trashed++
            RecruiterLabel.FILTERED_OUT -> counters.filteredOut++
            RecruiterLabel.JOB_LISTING -> counters.jobListings++
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
                reasonOf(evaluation)
            }

            is JobDescriptionEvaluation.Unknown -> {
                "The model could not evaluate the job description: ${evaluation.reason}"
            }
        }

    /**
     * What the model decided, and what is still missing.
     *
     * @param evaluation the evaluation of the job description.
     * @return the reason of the log.
     */
    private fun reasonOf(evaluation: JobDescriptionEvaluation.Evaluated): String =
        evaluation.missing?.let { "The model decided, $it is missing" } ?: "The model decided"

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
    }

    companion object {
        /** The event of the log of a screened message. */
        const val EVENT_SCREENED: String = "process-inbox.screened"

        /** The event of the log of an evaluated job description. */
        const val EVENT_EVALUATED: String = "process-inbox.evaluated"
    }
}
