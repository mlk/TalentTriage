package uk.me.michaellloydlee.recruiter.application

import uk.me.michaellloydlee.recruiter.application.screening.ScreeningService
import uk.me.michaellloydlee.recruiter.domain.screening.ScreeningDecision
import uk.me.michaellloydlee.recruiter.infrastructure.gmail.GmailMessagesApi
import uk.me.michaellloydlee.recruiter.infrastructure.logging.StructuredLogger

/**
 * Runs one pass of the pipeline over the unprocessed messages of the inbox.
 *
 * The messages that were not processed yet are fetched, screened and the ones
 * that are not recruiter mails are filtered out. The recruiter mails are
 * counted here, the deep evaluation of their attachments comes later.
 *
 * @param messages the message endpoints of the Gmail API.
 * @param screening the screening of the messages.
 * @param logger sink for the log of the screened messages.
 */
class ProcessInboxService(
    private val messages: GmailMessagesApi,
    private val screening: ScreeningService,
    private val logger: StructuredLogger,
) {
    /**
     * Runs one pass of the pipeline.
     *
     * @return what the run did.
     */
    suspend fun run(): ProcessInboxSummary {
        val pending = messages.listUnprocessed()
        var recruiters = 0
        var filteredOut = 0
        var unknown = 0
        pending.forEach { summary ->
            val message = messages.getMessage(summary.id)
            val outcome = screening.screen(message)
            logger.info(
                event = "$EVENT_SCREENED",
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
                ScreeningDecision.RECRUITER -> recruiters++
                ScreeningDecision.NOT_RECRUITER -> filteredOut++
                ScreeningDecision.UNKNOWN -> unknown++
            }
        }
        return ProcessInboxSummary(
            screened = pending.size,
            recruiters = recruiters,
            filteredOut = filteredOut,
            unknown = unknown,
        )
    }

    companion object {
        /** The event of the log of a screened message. */
        const val EVENT_SCREENED: String = "process-inbox.screened"
    }
}
