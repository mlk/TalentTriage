package uk.me.michaellloydlee.recruiter.domain.requirements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PayScaleTest {
    @Test
    fun theBandOfTheKeyIsTheBandOfTheScale() {
        val scale = scale()

        assertEquals(PayRequirement(70_000.0, "GBP"), scale.bandOf("1")?.pay)
    }

    @Test
    fun theOptionOfTheBandTellsTheLevelOfTheRole() {
        val scale = scale()

        assertEquals(
            "60000 GBP per year, the pay of the market of a junior role",
            scale.bandOf("0")?.option,
        )
    }

    @Test
    fun hasNoBandForAnUnknownKey() {
        val scale = scale()

        assertNull(scale.bandOf("9"))
        assertNull(scale.bandOf("one"))
    }

    @Test
    fun needsAtLeastTwoBands() {
        assertFailsWith<IllegalArgumentException> {
            PayScale(listOf(PayBand(PayRequirement(60_000.0, "GBP"), "junior")))
        }
    }

    private fun scale(): PayScale =
        PayScale(
            listOf(
                PayBand(PayRequirement(60_000.0, "GBP"), "junior"),
                PayBand(PayRequirement(70_000.0, "GBP"), "senior"),
            ),
        )
}
