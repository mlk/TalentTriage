package uk.me.michaellloydlee.recruiter.domain.screening

/**
 * The stage of the screening that made the decision.
 */
enum class ScreeningStage {
    /** The keyword and sender rules decided it, no model was asked. */
    HEURISTICS,

    /** The local model decided it. */
    LOCAL_MODEL,
}
