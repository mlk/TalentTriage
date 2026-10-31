package uk.me.michaellloydlee.recruiter.application.requirements

import uk.me.michaellloydlee.recruiter.domain.requirements.JobRequirements

/**
 * Generates the requirements of the job seeker from the CV.
 *
 * The profile of the job seeker is written by the chat model from the text of
 * the CV, the pays of the market come from the web and the decision model
 * names the minimum pay out of them. The days of office the job seeker allows
 * are not in the CV, the job seeker says them on the command line.
 *
 * @param profileWriter writes the profile of the job seeker.
 * @param salaryLookup looks for the pays of the market.
 * @param payChooser lets the decision model name the minimum pay.
 */
class RequirementsGenerator(
    private val profileWriter: CvProfileWriter,
    private val salaryLookup: SalaryScaleLookup,
    private val payChooser: PayChooser,
) {
    /**
     * Generates the requirements of the job seeker.
     *
     * @param cv the text of the CV.
     * @param maximumOfficeDays the number of days of office a job may demand.
     * @return the requirements of the job seeker, the reason why there are
     *   none.
     */
    suspend fun generate(
        cv: String,
        maximumOfficeDays: Int,
    ): RequirementsOutcome {
        val profile =
            when (val outcome = profileWriter.write(cv)) {
                is CvProfileOutcome.Failed -> return RequirementsOutcome.Failed(outcome.reason)
                is CvProfileOutcome.Written -> outcome.profile
            }
        val notes = mutableListOf<String>()
        if (profile.region.isBlank()) {
            notes.add("The region of the job seeker is not known, the pays of the market are looked for without it")
        }
        val scale = salaryLookup.scale(profile)
        when (scale) {
            is SalaryScaleOutcome.Default -> {
                notes.add("The pays of the market are not known: ${scale.reason}")
            }

            is SalaryScaleOutcome.FromMarket -> {
                notes.add("The pay of the market is ${scale.market.amount} ${scale.market.currency} per year")
            }
        }
        val pay =
            when (val outcome = payChooser.choose(cv, scale.scale)) {
                is PayOutcome.Failed -> {
                    return RequirementsOutcome.Failed(outcome.reason)
                }

                is PayOutcome.Unclear -> {
                    notes.add("The minimum pay is not set: ${outcome.reason}")
                    null
                }

                is PayOutcome.Chosen -> {
                    outcome.pay
                }
            }
        return RequirementsOutcome.Generated(
            requirements =
                JobRequirements(
                    minimumPay = pay,
                    maximumOfficeDays = maximumOfficeDays,
                    profile = profile.profile,
                ),
            notes = notes,
        )
    }
}

/**
 * The outcome of the generation of the requirements.
 */
sealed interface RequirementsOutcome {
    /**
     * The requirements of the job seeker.
     *
     * @property requirements the requirements of the job seeker.
     * @property notes what the generation had to say about the requirements.
     */
    data class Generated(
        val requirements: JobRequirements,
        val notes: List<String>,
    ) : RequirementsOutcome

    /**
     * The requirements could not be generated.
     *
     * @property reason why the requirements could not be generated.
     */
    data class Failed(
        val reason: String,
    ) : RequirementsOutcome
}
