package uk.me.michaellloydlee.recruiter.domain.requirements

/**
 * The requirements of the job seeker, the criteria the job descriptions are
 * measured against.
 *
 * @property minimumPay the pay the job seeker wants at least, `null` when the
 *   pay does not matter.
 * @property maximumOfficeDays the number of days of office per week a job may
 *   demand, the job seeker wants the remote friendly ones.
 * @property profile what the job description is compared with.
 */
data class JobRequirements(
    val minimumPay: PayRequirement?,
    val maximumOfficeDays: Int,
    val profile: String,
) {
    init {
        require(maximumOfficeDays >= 0) {
            "The maximum number of office days cannot be negative"
        }
        require(profile.isNotBlank()) {
            "The profile of the job seeker cannot be empty"
        }
    }

    /**
     * Whether the pay matters at all.
     */
    val caresAboutPay: Boolean
        get() = minimumPay != null
}

/**
 * The pay a job has to offer at least.
 *
 * @property amount the amount of the pay.
 * @property currency the currency of the amount, for example `EUR`.
 * @property period the period the amount is paid for, for example `year`.
 */
data class PayRequirement(
    val amount: Double,
    val currency: String,
    val period: String = "year",
) {
    init {
        require(amount > 0) {
            "The pay of the requirement cannot be zero or negative"
        }
        require(currency.isNotBlank()) {
            "The currency of the pay cannot be empty"
        }
    }

    /**
     * The pay as it is written in a question, without the decimals it does
     * not need.
     */
    val description: String
        get() = "${amountWithoutDecimals()} $currency per $period"

    private fun amountWithoutDecimals(): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
}
