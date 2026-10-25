package uk.me.michaellloydlee.recruiter.cli

import java.time.Duration

/**
 * Parses a compact duration like `30m`, `24h` or `7d`.
 *
 * @param value the duration to parse.
 * @return the duration.
 * @throws IllegalArgumentException when the value is not a duration.
 */
fun parseDuration(value: String): Duration {
    val match =
        DURATION.matchEntire(value.trim())
            ?: throw IllegalArgumentException(
                "'$value' is not a duration, use a number with a unit, for example 30m, 24h or 7d",
            )
    val amount = match.groupValues[AMOUNT_GROUP].toLong()
    return when (match.groupValues[UNIT_GROUP]) {
        "m" -> Duration.ofMinutes(amount)
        "h" -> Duration.ofHours(amount)
        "d" -> Duration.ofDays(amount)
        else -> throw IllegalArgumentException("'$value' has an unknown unit")
    }
}

private val DURATION = Regex("(\\d+)([mhd])")
private const val AMOUNT_GROUP = 1
private const val UNIT_GROUP = 2
