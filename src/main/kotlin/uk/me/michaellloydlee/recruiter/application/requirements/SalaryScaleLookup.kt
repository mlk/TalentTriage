package uk.me.michaellloydlee.recruiter.application.requirements

import uk.me.michaellloydlee.recruiter.domain.requirements.PayBand
import uk.me.michaellloydlee.recruiter.domain.requirements.PayRequirement
import uk.me.michaellloydlee.recruiter.domain.requirements.PayScale
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaWebSearchClient
import uk.me.michaellloydlee.recruiter.infrastructure.llm.OllamaWebSearchResult
import uk.me.michaellloydlee.recruiter.infrastructure.llm.WebSearchOutcome

/**
 * Looks for the pay of the role of the job seeker in the region of the job
 * seeker.
 *
 * The pages of the web hold the pay of the market: the scale of the pays the
 * model chooses the minimum pay from is built around them. When the web cannot
 * be reached, the default scale of the region is used, the caller is told why.
 *
 * @param search the client of the web search of Ollama.
 */
class SalaryScaleLookup(
    private val search: OllamaWebSearchClient,
) {
    /**
     * The scale of the pays of the market.
     *
     * @param profile the profile of the job seeker.
     * @return the scale of the market, the default scale when the web could
     *   not be reached.
     */
    suspend fun scale(profile: CvProfile): SalaryScaleOutcome =
        when (val outcome = search.search(query(profile))) {
            is WebSearchOutcome.Failed -> {
                SalaryScaleOutcome.Default(defaultScale(profile.region), outcome.reason)
            }

            is WebSearchOutcome.Found -> {
                scaleOf(profile.region, outcome.results)
            }
        }

    /**
     * The scale of the pays of the pages.
     *
     * @param region the place the job seeker works in.
     * @param pages the pages of the web the search found.
     * @return the scale of the market, the default scale when the pages hold
     *   no pay.
     */
    private fun scaleOf(
        region: String,
        pages: List<OllamaWebSearchResult>,
    ): SalaryScaleOutcome {
        val pays =
            pages.flatMap { page ->
                SalaryAmounts.of("${page.title} ${page.content}", currencyOf(region))
            }
        val market = marketOf(pays)
        if (market == null) {
            return SalaryScaleOutcome.Default(
                defaultScale(region),
                "The web search of Ollama found no pay of the market",
            )
        }
        return SalaryScaleOutcome.FromMarket(scaleAround(market), market)
    }

    /**
     * The query of the search.
     *
     * @param profile the profile of the job seeker.
     * @return what is searched for.
     */
    private fun query(profile: CvProfile): String {
        val region = profile.region.trim()
        return if (region.isEmpty()) {
            "average salary ${profile.role.trim()}"
        } else {
            "average salary ${profile.role.trim()} $region"
        }
    }

    /**
     * The pay of the market.
     *
     * The pays of the pages are grouped by their currency, the currency with
     * the most pays is the currency of the market and the middle of its pays
     * is the pay of the market.
     *
     * @param pays the pays of the pages.
     * @return the pay of the market, `null` when the pages hold no pay.
     */
    private fun marketOf(pays: List<SalaryAmount>): SalaryAmount? {
        val group = pays.groupBy { it.currency }.values.maxByOrNull { it.size } ?: return null
        val amounts = group.map { it.amount }.sorted()
        val middle = amounts.size / 2
        val amount =
            if (amounts.size % 2 == 1) {
                amounts[middle]
            } else {
                (amounts[middle - 1] + amounts[middle]) / 2
            }
        return SalaryAmount(amount, group.first().currency)
    }

    /**
     * The scale around the pay of the market.
     *
     * @param market the pay of the market.
     * @return the scale of the pays.
     */
    private fun scaleAround(market: SalaryAmount): PayScale = scaleOf(market.amount, market.currency)

    /**
     * The default scale of a region.
     *
     * @param region the place the job seeker works in.
     * @return the scale of the pays of the region.
     */
    private fun defaultScale(region: String): PayScale = scaleOf(DEFAULT_MARKET, currencyOf(region))

    /**
     * The scale of the pays of a market.
     *
     * The pays of the market are the pays of the levels of the role, the model
     * chooses the level of the job seeker out of them.
     *
     * @param market the pay of the market.
     * @param currency the currency of the market.
     * @return the scale of the pays.
     */
    private fun scaleOf(
        market: Double,
        currency: String,
    ): PayScale =
        PayScale(
            FACTORS.mapIndexed { index, factor ->
                PayBand(
                    pay = PayRequirement(round(market * factor), currency),
                    level = LEVELS[index],
                )
            },
        )

    /**
     * The currency of a region.
     *
     * The pages of the web name the currency of their pays, the currency of
     * the region is the fallback for the pays that are written without one.
     * The names of the places are matched as they are written, a place that
     * is not known has the currency of the default.
     *
     * @param region the place the job seeker works in.
     * @return the currency of the region.
     */
    private fun currencyOf(region: String): String {
        val place = region.lowercase()
        return when {
            place.contains("united kingdom") || place.contains("london") || place.contains("edinburgh") -> "GBP"
            place.contains("united states") || place.contains("new york") || place.contains("san francisco") -> "USD"
            place.contains("switzerland") || place.contains("zurich") -> "CHF"
            else -> DEFAULT_CURRENCY
        }
    }

    /**
     * The amount of a pay, rounded to the thousand.
     *
     * @param amount the amount of the pay.
     * @return the rounded amount.
     */
    private fun round(amount: Double): Double = Math.round(amount / ROUNDING) * ROUNDING

    companion object {
        /** The factors of the pays around the pay of the market. */
        private val FACTORS: List<Double> = listOf(0.7, 0.9, 1.1, 1.35)

        /** The levels of the role the pays of the market are the pays of. */
        private val LEVELS: List<String> = listOf("junior", "mid level", "senior", "lead")

        /** The pay of the market when the web cannot be reached. */
        private const val DEFAULT_MARKET: Double = 75_000.0

        /** The currency of the regions that are not known. */
        private const val DEFAULT_CURRENCY: String = "EUR"

        /** The amounts are rounded to the thousand. */
        private const val ROUNDING: Double = 1_000.0
    }
}

/**
 * The outcome of the lookup of the pay of the market.
 */
sealed interface SalaryScaleOutcome {
    /**
     * The pays of the scale.
     */
    val scale: PayScale

    /**
     * The scale of the market.
     *
     * @property scale the pays of the market.
     * @property market the pay of the market.
     */
    data class FromMarket(
        override val scale: PayScale,
        val market: SalaryAmount,
    ) : SalaryScaleOutcome

    /**
     * The default scale, the market was not found.
     *
     * @property scale the pays of the region.
     * @property reason why the market was not found.
     */
    data class Default(
        override val scale: PayScale,
        val reason: String,
    ) : SalaryScaleOutcome
}
