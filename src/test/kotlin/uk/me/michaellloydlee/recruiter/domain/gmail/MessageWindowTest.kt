package uk.me.michaellloydlee.recruiter.domain.gmail

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MessageWindowTest {
    @Test
    fun readsAnIsoDurationAsAWindow() {
        assertEquals(MessageWindow(Duration.ofHours(24)), messageWindow("PT24H"))
        assertEquals(MessageWindow(Duration.ofDays(1)), messageWindow("P1D"))
    }

    @Test
    fun refusesATextThatIsNotADuration() {
        val failure = assertFailsWith<IllegalArgumentException> { messageWindow("3y") }

        assertTrue(failure.message!!.contains("is not a duration"))
    }

    @Test
    fun refusesAWindowOfZero() {
        val failure = assertFailsWith<IllegalArgumentException> { messageWindow("PT0S") }

        assertTrue(failure.message!!.contains("longer than zero"))
    }

    @Test
    fun refusesANegativeWindow() {
        val failure = assertFailsWith<IllegalArgumentException> { messageWindow("PT-1H") }

        assertTrue(failure.message!!.contains("longer than zero"))
    }

    @Test
    fun theDefaultWindowIsTheLastDay() {
        assertEquals(Duration.ofHours(24), MessageWindow.LAST_DAY.duration)
    }
}
