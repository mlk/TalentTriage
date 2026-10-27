package uk.me.michaellloydlee.recruiter.application.screening

/**
 * How the pipeline verifies whether a message is from a recruiter.
 */
enum class RecruiterVerificationMode {
    /** A decision model of Ollama answers a typed question. */
    DECISION,

    /** A chat model of Ollama answers in words. */
    LLM,
}
