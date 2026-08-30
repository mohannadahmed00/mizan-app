package com.giraffe.mizanapp.domain.leaderboard

import com.giraffe.mizanapp.domain.time.DayBoundary
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * SC-005 — the criterion this increment exists for. A region's zone is always
 * exactly the participant's own reported zone (one-to-one mapping, FR-014), so
 * the leaderboard day is the device's own day by construction *only if*
 * [DayBoundary] and [periodFor] are actually zone-correct rather than quietly
 * sharing one UTC-derived date across every region.
 *
 * The chosen instant is deliberately one where that would be observable: at
 * 2026-08-28T20:00:00Z it is still Friday (the week's last day) in Hawaii
 * (Honolulu) and Arabia (Riyadh), but already Saturday (the next week's
 * first day) in Pakistan (Karachi) — a real midnight-straddling case across
 * the four seeded regions' >= 12-hour offset spread (Honolulu UTC-10 to
 * Karachi UTC+5), landing exactly on the boundary a Friday-only catalogue
 * task depends on.
 */
class RegionalPeriodBoundaryTest {

    private val instant = Instant.parse("2026-08-28T20:00:00Z")

    @Test
    fun daily_leaderboard_day_is_the_regions_own_zone_local_date() {
        listOf(HONOLULU, RIYADH, CAIRO, KARACHI).forEach { zone ->
            val deviceToday = DayBoundary.dateAt(instant, zone)
            val period = periodFor(PeriodKind.DAILY, deviceToday, zone, RegionId(zone.id))

            assertEquals("region $zone", deviceToday, period.start)
            assertEquals("region $zone", deviceToday, period.endInclusive)
        }
    }

    @Test
    fun the_chosen_instant_actually_straddles_midnight_between_the_widest_offset_pair() {
        val honoluluDate = DayBoundary.dateAt(instant, HONOLULU)
        val karachiDate = DayBoundary.dateAt(instant, KARACHI)

        assertNotEquals("a weak instant would make every other assertion vacuous", honoluluDate, karachiDate)
        assertEquals(DayOfWeek.FRIDAY, honoluluDate.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, karachiDate.dayOfWeek)
    }

    @Test
    fun weekly_period_lands_each_region_in_its_own_correct_week_at_the_same_instant() {
        val honoluluToday = DayBoundary.dateAt(instant, HONOLULU)
        val riyadhToday = DayBoundary.dateAt(instant, RIYADH)
        val karachiToday = DayBoundary.dateAt(instant, KARACHI)

        val honoluluWeek = periodFor(PeriodKind.WEEKLY, honoluluToday, HONOLULU, RegionId("hawaii-honolulu"))
        val riyadhWeek = periodFor(PeriodKind.WEEKLY, riyadhToday, RIYADH, RegionId("arabia-riyadh"))
        val karachiWeek = periodFor(PeriodKind.WEEKLY, karachiToday, KARACHI, RegionId("pakistan-karachi"))

        // Honolulu and Riyadh are both still on Friday: the outgoing week.
        assertEquals(honoluluToday, honoluluWeek.endInclusive)
        assertEquals(riyadhToday, riyadhWeek.endInclusive)
        assertEquals(WeekBoundary.weekContaining(honoluluToday).start, honoluluWeek.start)

        // Karachi has already turned into Saturday: the next week's first day —
        // never the same week as Honolulu/Riyadh at this same instant.
        assertEquals(karachiToday, karachiWeek.start)
        assertNotEquals("a UTC-shared date bug would collapse these into one week", honoluluWeek.start, karachiWeek.start)
    }

    private companion object {
        val HONOLULU: ZoneId = ZoneId.of("Pacific/Honolulu")
        val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")
        val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")
        val KARACHI: ZoneId = ZoneId.of("Asia/Karachi")
    }
}
