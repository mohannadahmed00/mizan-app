package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Crossing local midnight, with a clock the test controls.
 *
 * Rollover is untestable against the real clock — a test that waits for
 * midnight is not a test.
 */
class RolloverTest {

    private val cairo = ZoneId.of("Africa/Cairo")

    private fun clockAt(date: LocalDate, time: LocalTime): FakeTimeProvider {
        val provider = FakeTimeProvider(zone = cairo)
        provider.setDate(date, time)
        return provider
    }

    @Test
    fun `two seconds past 23 59 59 is the next day`() {
        val clock = clockAt(LocalDate.of(2026, 3, 14), LocalTime.of(23, 59, 59))
        assertEquals(LocalDate.of(2026, 3, 14), clock.today())

        clock.advanceBy(Duration.ofSeconds(2))

        assertEquals(LocalDate.of(2026, 3, 15), clock.today())
    }

    @Test
    fun `a plan built before rollover is untouched by it`() {
        val clock = clockAt(LocalDate.of(2026, 3, 14), LocalTime.of(23, 0))
        val before = buildDayPlan(
            DayFixtures.catalogue, version = 1, date = clock.today(), origin = PlanOrigin.OPENED, newId = DayFixtures.sequentialIds(),
        )
        val snapshot = before.copy()

        clock.advanceBy(Duration.ofHours(2))
        val after = buildDayPlan(
            DayFixtures.catalogue, version = 1, date = clock.today(), origin = PlanOrigin.OPENED, newId = DayFixtures.sequentialIds(),
        )

        assertEquals("the earlier plan must be unchanged", snapshot, before)
        assertNotEquals("rollover must produce a new date", before.date, after.date)
    }

    @Test
    fun `rollover from friday to saturday changes the available total`() {
        val clock = clockAt(LocalDate.of(2026, 3, 20), LocalTime.of(23, 30))
        val friday = buildDayPlan(
            DayFixtures.catalogue, version = 1, date = clock.today(), origin = PlanOrigin.OPENED, newId = DayFixtures.sequentialIds(),
        )

        clock.advanceBy(Duration.ofHours(1))
        val saturday = buildDayPlan(
            DayFixtures.catalogue, version = 1, date = clock.today(), origin = PlanOrigin.OPENED, newId = DayFixtures.sequentialIds(),
        )

        assertEquals(76, friday.availablePoints)
        assertEquals(69, saturday.availablePoints)
    }

    @Test
    fun `staying within the day does not roll over`() {
        val clock = clockAt(LocalDate.of(2026, 3, 14), LocalTime.of(6, 0))
        val morning = clock.today()

        clock.advanceBy(Duration.ofHours(12))

        assertEquals(morning, clock.today())
    }
}
