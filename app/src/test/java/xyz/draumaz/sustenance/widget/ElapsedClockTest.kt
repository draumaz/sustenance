package xyz.draumaz.sustenance.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * The fasting widget's clock is a TextClock showing "now" in a shifted time zone; these check
 * that the time it would show really is the time since the last meal.
 */
class ElapsedClockTest {

    private val now = Instant.parse("2026-11-21T20:50:00Z")

    /** What a TextClock in [ElapsedClock.timeZoneId] reads at [now], to the minute. */
    private fun shown(clock: ElapsedClock, at: Instant = now): LocalTime =
        LocalTime.ofInstant(at, ZoneId.of(clock.timeZoneId)).withSecond(0).withNano(0)

    private fun assertShows(elapsed: Duration, expectedDays: Long, expected: LocalTime) {
        val clock = ElapsedClock.of(now.minus(elapsed), now)
        assertEquals("days for $elapsed", expectedDays, clock.days)
        assertEquals("time for $elapsed", expected, shown(clock))
    }

    @Test
    fun showsMinutesSinceTheLastMeal() = assertShows(Duration.ofMinutes(36), 0, LocalTime.of(0, 36))

    @Test
    fun handlesFastsThatCrossMidnight() = assertShows(Duration.ofHours(14).plusMinutes(5), 0, LocalTime.of(14, 5))

    @Test
    fun wrapsPastADayAndCountsTheDays() {
        assertShows(Duration.ofHours(25).plusMinutes(3), 1, LocalTime.of(1, 3))
        assertShows(Duration.ofHours(47).plusMinutes(59), 1, LocalTime.of(23, 59))
        assertShows(Duration.ofHours(48), 2, LocalTime.of(0, 0))
    }

    @Test
    fun keepsTickingCorrectlyAfterRendering() {
        val clock = ElapsedClock.of(now.minus(Duration.ofMinutes(36)), now)
        assertEquals(LocalTime.of(3, 36), shown(clock, now.plus(Duration.ofHours(3))))
    }

    @Test
    fun aMealInTheFutureReadsAsJustEaten() {
        val clock = ElapsedClock.of(now.plus(Duration.ofMinutes(5)), now)
        assertEquals(0L, clock.days)
        assertEquals(LocalTime.MIDNIGHT, shown(clock))
    }

    @Test
    fun offsetStaysWithinWhatGmtZoneIdsAccept() {
        // Every minute-of-day for the last meal, so every possible offset is produced once.
        for (minute in 0 until 24 * 60) {
            val lastLog = Instant.parse("2026-11-21T00:00:00Z").plus(Duration.ofMinutes(minute.toLong()))
            val id = ElapsedClock.of(lastLog, now).timeZoneId
            val offset = ZoneId.of(id).rules.getOffset(now).totalSeconds
            assertTrue("offset $id for minute $minute", offset in -12 * 3600..12 * 3600)
            // TextClock resolves the ID with java.util.TimeZone, which silently falls back to
            // plain GMT for IDs it can't parse: that would show the wrong time with no error.
            assertEquals("TimeZone offset for $id", offset * 1000, TimeZone.getTimeZone(id).rawOffset)
        }
    }

    @Test
    fun nextRolloverIsTheNextWholeDayOfTheFast() {
        val lastLog = Instant.parse("2026-11-20T19:14:30Z") // 25h36m before now
        assertEquals(Instant.parse("2026-11-22T19:14:00Z"), ElapsedClock.nextRollover(lastLog, now))
    }

    @Test
    fun progressIsTheShareOfTheGoalCappedAtOne() {
        assertEquals(0.5f, FastState(now.minus(Duration.ofHours(8)), 16f, now).progress, 1e-4f)
        assertEquals(1f, FastState(now.minus(Duration.ofHours(30)), 16f, now).progress, 0f)
        assertEquals(0f, FastState(null, 16f, now).progress, 0f)
        assertEquals(0f, FastState(now.plus(Duration.ofHours(1)), 16f, now).progress, 0f)
    }
}
