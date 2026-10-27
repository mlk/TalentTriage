package uk.me.michaellloydlee.recruiter.application.evaluation

/**
 * How the pipeline evaluates the job descriptions.
 */
enum class JobDescriptionEvaluationMode {
    /** A decision model of Ollama answers a typed question. */
    DECISION,

    /** A chat model of Ollama answers in words. */
    CHAT,
}
