package uk.me.michaellloydlee.recruiter.domain.requirements

/**
 * The pays the model chooses the minimum pay of the job seeker from.
 *
 * The scale holds the pays of the market for the role of the job seeker, from
 * the lowest to the highest one. The model names the pay of the scale the job
 * seeker wants at least.
 *
 * @property bands the pays of the scale, from the lowest to the highest one.
 */
data class PayScale(
    val bands: List<PayBand>,
) {
    init {
        require(bands.size >= MINIMUM_BANDS) {
            "A scale of the pays needs at least $MINIMUM_BANDS bands"
        }
    }

    /**
     * The band of the scale with the given key.
     *
     * The key of a band is its place in the scale, the model answers with the
     * key of the band it chose.
     *
     * @param key the key the model answered with.
     * @return the band of the key, `null` when the key is not one of the scale.
     */
    fun bandOf(key: String): PayBand? {
        val index = key.toIntOrNull() ?: return null
        return bands.getOrNull(index)
    }

    companion object {
        /** How many bands a scale holds at least, the model needs options. */
        const val MINIMUM_BANDS: Int = 2
    }
}

/**
 * A pay of the market.
 *
 * @property pay the pay of the band.
 * @property level the level of the role the pay is the pay of, for example
 *   `senior`.
 */
data class PayBand(
    val pay: PayRequirement,
    val level: String,
) {
    /**
     * The option of the question of the pay.
     *
     * The model does not know the pays of the market, the level of the role
     * tells it what the pay is the pay of.
     */
    val option: String
        get() = "${pay.description}, the pay of the market of a $level role"
}
