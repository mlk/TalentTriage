package uk.me.michaellloydlee.recruiter.cli

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DurationParserTest {
    @Test
    fun parsesMinutes() {
        assertEquals(Duration.ofMinutes(30), parseDuration("30m"))
    }

    @Test
    fun parsesHours() {
        assertEquals(Duration.ofHours(24), parseDuration("24h"))
    }

    @Test
    fun parsesDays() {
        assertEquals(Duration.ofDays(7), parseDuration("7d"))
    }

    @Test
    fun ignoresSurroundingSpaces() {
        assertEquals(Duration.ofHours(2), parseDuration(" 2h "))
    }

    @Test
    fun failsOnAnUnknownUnit() {
        val error = assertFailsWith<IllegalArgumentException> { parseDuration("3y") }

        assertTrue(error.message!!.contains("not a duration"))
    }

    @Test
    fun failsOnAMissingAmount() {
        assertFailsWith<IllegalArgumentException> { parseDuration("h") }
    }

    @Test
    fun failsOnAMissingUnit() {
        assertFailsWith<IllegalArgumentException> { parseDuration("24") }
    }

    @Test
    fun failsOnAnEmptyValue() {
        assertFailsWith<IllegalArgumentException> { parseDuration("") }
    }
}
