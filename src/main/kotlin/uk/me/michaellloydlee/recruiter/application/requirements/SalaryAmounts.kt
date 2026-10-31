package uk.me.michaellloydlee.recruiter.application.requirements

/**
 * A pay of the market.
 *
 * @property amount the amount of the pay.
 * @property currency the currency of the amount.
 */
data class SalaryAmount(
    val amount: Double,
    val currency: String,
)

/**
 * The pays a text names.
 *
 * The pages of the web name the pay of a role in many ways: `£72,500`,
 * `72.500 EUR`, `85k` or `salary of 120000`. The pays of a text are the
 * amounts that are written like that, the amounts outside of the range of a
 * pay are not pays.
 */
object SalaryAmounts {
    /**
     * The pays a text names.
     *
     * @param text the text of a page of the web.
     * @param defaultCurrency the currency of the pays that are written without
     *   one.
     * @return the pays of the text.
     */
    fun of(
        text: String,
        defaultCurrency: String,
    ): List<SalaryAmount> {
        val pays = mutableListOf<SalaryAmount>()
        val withCurrency = mutableListOf<IntRange>()
        CURRENCY_FIRST.findAll(text).forEach { match ->
            withCurrency.add(match.range)
            pay(match)?.let { pays.add(SalaryAmount(it, currencyOf(match))) }
        }
        CURRENCY_LAST.findAll(text).forEach { match ->
            withCurrency.add(match.range)
            pay(match)?.let { pays.add(SalaryAmount(it, currencyOf(match))) }
        }
        // The pays that are written with a currency are found already, the
        // word of the pay may not name them a second time.
        SALARY_WORD.findAll(without(text, withCurrency)).forEach { match ->
            pay(match)?.let { pays.add(SalaryAmount(it, defaultCurrency)) }
        }
        return pays.distinct()
    }

    /**
     * The text without the parts that hold a pay with a currency.
     *
     * @param text the text of a page of the web.
     * @param ranges the parts of the text that hold a pay with a currency.
     * @return the text without those parts.
     */
    private fun without(
        text: String,
        ranges: List<IntRange>,
    ): String {
        val builder = StringBuilder(text)
        ranges.forEach { range ->
            range.forEach { index ->
                if (index in builder.indices) {
                    builder.setCharAt(index, ' ')
                }
            }
        }
        return builder.toString()
    }

    /**
     * The amount of a pay.
     *
     * @param match the match of a pay.
     * @return the amount of the pay, `null` when it is not the amount of a
     *   pay.
     */
    private fun pay(match: MatchResult): Double? {
        val amount = amountOf(match.groups["amount"]?.value.orEmpty()) ?: return null
        val thousands = match.groups["thousands"]?.value.orEmpty()
        val pay = if (thousands.isNotEmpty()) amount * THOUSAND else amount
        return if (pay in MINIMUM_AMOUNT..MAXIMUM_AMOUNT) pay else null
    }

    /**
     * The amount of the digits of a pay.
     *
     * @param raw the digits of the amount.
     * @return the amount, `null` when the digits are not a number.
     */
    private fun amountOf(raw: String): Double? {
        val withoutSeparators = raw.replace(",", "")
        val normalized =
            if (THOUSANDS_SEPARATOR.matches(withoutSeparators)) {
                withoutSeparators.replace(".", "")
            } else {
                withoutSeparators
            }
        return normalized.toDoubleOrNull()
    }

    /**
     * The currency of a symbol or of a code.
     *
     * @param match the match of a pay.
     * @return the currency, the code of the currency when it is one.
     */
    private fun currencyOf(match: MatchResult): String {
        val marker = match.groups["currency"]?.value.orEmpty()
        return SYMBOLS[marker] ?: marker
    }

    /** A pay with the currency in front of it, `£72,500`. */
    private val CURRENCY_FIRST =
        Regex("""(?<currency>GBP|EUR|USD|CHF|£|€|\$)\s*(?<amount>\d[\d.,]*)\s*(?<thousands>k|K)?""")

    /** A pay with the currency behind it, `72.500 EUR`. */
    private val CURRENCY_LAST =
        Regex("""(?<amount>\d[\d.,]*)\s*(?<thousands>k|K)?\s*(?<currency>GBP|EUR|USD|CHF|£|€|\$)""")

    /** A pay that is written with the word of the pay, `salary of 120000`. */
    private val SALARY_WORD =
        Regex("""(?i:salary|pay|compensation|base)[^\d£€\$]{0,20}?(?<amount>\d[\d.,]*)\s*(?<thousands>k|K)?""")

    /** The digits of a pay that are written with a point as the separator of the thousands. */
    private val THOUSANDS_SEPARATOR = Regex("""\d+\.\d{3}""")

    /** The symbols of the currencies. */
    private val SYMBOLS: Map<String, String> =
        mapOf(
            "£" to "GBP",
            "€" to "EUR",
            "\$" to "USD",
        )

    /** The smallest amount that is a pay of a job. */
    private const val MINIMUM_AMOUNT: Double = 10_000.0

    /** The largest amount that is a pay of a job. */
    private const val MAXIMUM_AMOUNT: Double = 1_000_000.0

    /** The amount of a pay that is written with a `k`. */
    private const val THOUSAND: Double = 1_000.0
}
