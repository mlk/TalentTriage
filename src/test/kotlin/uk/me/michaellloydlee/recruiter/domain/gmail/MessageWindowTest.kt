package uk.me.michaellloydlee.recruiter.domain.gmail

import java.time.Duration
import java.time.Instant
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

    @Test
    fun theWindowOfARunIsTheGapSinceTheLastRun() {
        val window = runWindow(lastRun = NOW.minusSeconds(3600), now = NOW, given = null)

        assertEquals(MessageWindow(Duration.ofHours(1)), window)
    }

    @Test
    fun theWindowOfTheCommandLineWins() {
        val given = MessageWindow(Duration.ofHours(6))

        val window = runWindow(lastRun = NOW.minusSeconds(3600), now = NOW, given = given)

        assertEquals(given, window)
    }

    @Test
    fun theWindowOfTheFirstRunIsTheLastDay() {
        val window = runWindow(lastRun = null, now = NOW, given = null)

        assertEquals(MessageWindow.LAST_DAY, window)
    }

    @Test
    fun theWindowIsTheLastDayWhenTheLastRunIsInTheFuture() {
        val window = runWindow(lastRun = NOW.plusSeconds(60), now = NOW, given = null)

        assertEquals(MessageWindow.LAST_DAY, window)
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-30T19:00:00Z")
    }
}
