package uk.me.michaellloydlee.recruiter.application.screening

import uk.me.michaellloydlee.recruiter.domain.gmail.EmailMessage

/**
 * Decides whether a message is from a recruiter or a job listing.
 *
 * The pipeline has two implementations: the decision model of Ollama, which
 * answers a typed question, and a chat model, which answers in words. The
 * command line chooses between them.
 */
interface RecruiterVerifier {
    /**
     * Verifies a message.
     *
     * @param message the message to look at.
     * @return the verdict, an unknown verdict leaves the message alone.
     */
    suspend fun verify(message: EmailMessage): RecruiterVerification
}
