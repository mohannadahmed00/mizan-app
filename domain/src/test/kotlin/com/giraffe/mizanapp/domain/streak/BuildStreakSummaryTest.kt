package com.giraffe.mizanapp.domain.streak

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildStreakSummaryTest {

    private val today = LocalDate.parse("2026-08-19")
    private val recordStart = LocalDate.parse("2026-07-01")
    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private val noon: Instant = today.atTime(12, 0).atZone(zone).toInstant()

    private fun dates(vararg iso: String) = iso.map(LocalDate::parse)

    private fun build(
        consistencyDates: List<LocalDate>,
        today: LocalDate = this.today,
        now: Instant = noon,
        dayEndsAt: Instant = today.plusDays(1).atStartOfDay(zone).toInstant(),
        recordStart: LocalDate? = this.recordStart,
    ) = buildStreakSummary(consistencyDates, today, now, dayEndsAt, recordStart)

    @Test
    fun run_of_five_ending_today() {
        val summary = build(dates("2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19"))

        assertEquals(5, summary.current)
        assertEquals(5, summary.longest)
        assertTrue(summary.todayCounted)
        assertEquals(today, summary.lastActiveDate)
    }

    @Test
    fun run_of_five_ending_yesterday_nothing_today() {
        val summary = build(dates("2026-08-14", "2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18"))

        assertEquals(5, summary.current)
        assertFalse(summary.todayCounted)
        assertEquals(LocalDate.parse("2026-08-18"), summary.lastActiveDate)
    }

    @Test
    fun most_recent_is_day_before_yesterday_current_is_zero() {
        val summary = build(dates("2026-08-15", "2026-08-16", "2026-08-17"))

        assertEquals(0, summary.current)
        assertFalse(summary.todayCounted)
    }

    @Test
    fun empty_record() {
        val summary = build(emptyList())

        assertEquals(0, summary.current)
        assertEquals(0, summary.longest)
        assertNull(summary.lastActiveDate)
    }

    @Test
    fun gap_then_three_day_run_ending_today() {
        val twelveDayRun = (0 until 12).map { today.minusDays(20L - it) }
        val threeDayRun = listOf(today.minusDays(2), today.minusDays(1), today)
        val summary = build(twelveDayRun + threeDayRun)

        assertEquals(3, summary.current)
        assertEquals(12, summary.longest)
    }

    @Test
    fun gaps_of_two_and_seven_days_break_the_run() {
        val twoDayGap = build(
            dates("2026-08-10", "2026-08-11", "2026-08-14", "2026-08-15"),
            today = LocalDate.parse("2026-08-15"),
        )
        assertEquals(2, twoDayGap.current)

        val sevenDayGap = build(
            dates("2026-08-01", "2026-08-02", "2026-08-09", "2026-08-10"),
            today = LocalDate.parse("2026-08-10"),
        )
        assertEquals(2, sevenDayGap.current)
    }

    @Test
    fun unbroken_run_reaching_record_start_is_not_a_break() {
        val allDates = generateSequence(recordStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .toList()

        val summary = build(allDates)

        assertEquals(allDates.size, summary.current)
        assertEquals(allDates.size, summary.longest)
    }

    @Test
    fun future_dates_are_ignored() {
        val run = dates("2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19")
        val withFuture = run + dates("2026-08-20", "2026-08-21")

        val summary = build(withFuture)

        assertEquals(5, summary.current)
        assertEquals(5, summary.longest)
        assertEquals(today, summary.lastActiveDate)
    }

    @Test
    fun current_never_exceeds_longest_across_every_case() {
        val cases = listOf(
            emptyList(),
            dates("2026-08-19"),
            dates("2026-08-01", "2026-08-02", "2026-08-18", "2026-08-19"),
        )
        for (c in cases) {
            val summary = build(c)
            assertTrue("current=${summary.current} longest=${summary.longest}", summary.current <= summary.longest)
        }
    }

    @Test
    fun duplicated_date_changes_nothing() {
        val withDup = dates("2026-08-17", "2026-08-18", "2026-08-19", "2026-08-19", "2026-08-19")
        val without = dates("2026-08-17", "2026-08-18", "2026-08-19")

        assertEquals(build(without), build(withDup))
    }

    @Test
    fun elapsed_date_missing_between_two_runs_breaks_at_the_gap() {
        // 08-17 is missing; only the trailing segment (08-18, 08-19) counts.
        val summary = build(dates("2026-08-15", "2026-08-16", "2026-08-18", "2026-08-19"))

        assertEquals(2, summary.current)
        assertEquals(2, summary.longest)
    }

    @Test
    fun break_notice_shown_when_last_active_is_seven_days_ago() {
        val summary = build(dates("2026-08-10", "2026-08-11", "2026-08-12"))

        assertEquals(0, summary.current)
        assertTrue(summary.showBreakNotice)
        assertEquals(7, summary.recentActivity.size)
    }

    @Test
    fun break_notice_not_shown_when_last_active_is_eight_days_ago() {
        val summary = build(dates("2026-08-09", "2026-08-10", "2026-08-11"))

        assertEquals(0, summary.current)
        assertFalse(summary.showBreakNotice)
    }

    @Test
    fun break_notice_not_shown_for_a_live_run() {
        val summary = build(dates("2026-08-19"))

        assertTrue(summary.current >= 1)
        assertFalse(summary.showBreakNotice)
    }

    @Test
    fun break_notice_not_shown_for_an_empty_record() {
        val summary = build(emptyList())

        assertEquals(0, summary.longest)
        assertFalse(summary.showBreakNotice)
    }

    @Test
    fun recent_activity_always_has_seven_entries() {
        assertEquals(7, build(emptyList()).recentActivity.size)
        assertEquals(7, build(dates("2026-08-19")).recentActivity.size)
    }

    @Test
    fun at_risk_when_live_run_not_counted_today_and_after_twenty_hundred() {
        val summary = build(dates("2026-08-18"), now = today.atTime(20, 0).atZone(zone).toInstant())

        assertTrue(summary.current >= 1)
        assertFalse(summary.todayCounted)
        assertTrue(summary.isAtRisk)
    }

    @Test
    fun not_at_risk_at_nineteen_fifty_nine() {
        val summary = build(dates("2026-08-18"), now = today.atTime(19, 59).atZone(zone).toInstant())

        assertFalse(summary.isAtRisk)
    }

    @Test
    fun not_at_risk_with_zero_current_streak() {
        val summary = build(dates("2026-08-16"), now = today.atTime(21, 0).atZone(zone).toInstant())

        assertEquals(0, summary.current)
        assertFalse(summary.isAtRisk)
    }

    @Test
    fun not_at_risk_when_today_already_counted() {
        val summary = build(
            dates("2026-08-18", "2026-08-19"),
            now = today.atTime(21, 0).atZone(zone).toInstant(),
        )

        assertTrue(summary.todayCounted)
        assertFalse(summary.isAtRisk)
    }
}
