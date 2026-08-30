package com.giraffe.mizanapp.domain.leaderboard

import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regional spans must agree with the app's one existing calendar definition. */
class PeriodForTest {

    private val regionId = RegionId("test-region")

    @Test
    fun daily_period_starts_and_ends_on_the_supplied_date() {
        val date = LocalDate.parse("2026-08-29")

        val period = periodFor(PeriodKind.DAILY, date, ZoneId.of("Africa/Cairo"), regionId)

        assertEquals(date, period.start)
        assertEquals(date, period.endInclusive)
        assertEquals(regionId, period.regionId)
    }

    @Test
    fun weekly_period_delegates_to_the_existing_saturday_to_friday_boundary() {
        val date = LocalDate.parse("2026-09-03")
        val existingWeek = WeekBoundary.weekContaining(date)

        val period = periodFor(PeriodKind.WEEKLY, date, ZoneId.of("Asia/Riyadh"), regionId)

        assertEquals(existingWeek.start, period.start)
        assertEquals(existingWeek.dates.last(), period.endInclusive)
    }

    @Test
    fun monthly_period_is_the_calendar_month_containing_the_date() {
        val period = periodFor(
            PeriodKind.MONTHLY,
            LocalDate.parse("2028-02-12"),
            ZoneId.of("Africa/Cairo"),
            regionId,
        )

        assertEquals(LocalDate.parse("2028-02-01"), period.start)
        assertEquals(LocalDate.parse("2028-02-29"), period.endInclusive)
    }

    @Test
    fun instant_near_utc_midnight_uses_the_expected_day_in_a_far_offset_zone() {
        val zone = ZoneId.of("Pacific/Kiritimati")
        val localDate = Instant.parse("2026-08-28T12:30:00Z").atZone(zone).toLocalDate()

        val period = periodFor(PeriodKind.DAILY, localDate, zone, regionId)

        assertEquals(LocalDate.parse("2026-08-29"), period.start)
        assertEquals(LocalDate.parse("2026-08-29"), period.endInclusive)
    }
}
