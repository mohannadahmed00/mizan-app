package com.giraffe.mizanapp.domain.streak

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentActivityTest {

    private val today = LocalDate.parse("2026-08-19")
    private val recordStart = LocalDate.parse("2026-08-17")

    private fun dates(vararg iso: String) = iso.map(LocalDate::parse).toSet()

    @Test
    fun always_has_exactly_seven_entries_oldest_first_today_last() {
        val result = buildRecentActivity(emptySet(), today, recordStart)

        assertEquals(7, result.size)
        assertEquals(today.minusDays(6), result.first().date)
        assertEquals(today, result.last().date)
    }

    @Test
    fun always_seven_entries_with_empty_record_and_no_record_start() {
        assertEquals(7, buildRecentActivity(emptySet(), today, null).size)
    }

    @Test
    fun a_date_in_the_set_reads_counted() {
        val result = buildRecentActivity(dates("2026-08-18"), today, recordStart)

        val entry = result.first { it.date == LocalDate.parse("2026-08-18") }
        assertEquals(ActivityState.COUNTED, entry.state)
    }

    @Test
    fun an_elapsed_date_on_or_after_record_start_not_in_the_set_reads_not_recorded() {
        val result = buildRecentActivity(emptySet(), today, recordStart)

        val entry = result.first { it.date == LocalDate.parse("2026-08-18") }
        assertEquals(ActivityState.NOT_RECORDED, entry.state)
    }

    @Test
    fun today_not_in_the_set_reads_today_pending_not_not_recorded() {
        val result = buildRecentActivity(emptySet(), today, recordStart)

        val entry = result.first { it.date == today }
        assertEquals(ActivityState.TODAY_PENDING, entry.state)
    }

    @Test
    fun a_date_before_record_start_reads_outside_record_not_not_recorded() {
        val result = buildRecentActivity(emptySet(), today, recordStart)

        val entry = result.first { it.date == LocalDate.parse("2026-08-16") }
        assertEquals(ActivityState.OUTSIDE_RECORD, entry.state)
    }

    @Test
    fun no_record_start_means_everything_but_today_is_outside_the_record() {
        val result = buildRecentActivity(emptySet(), today, null)

        result.dropLast(1).forEach { assertEquals(ActivityState.OUTSIDE_RECORD, it.state) }
        assertEquals(ActivityState.TODAY_PENDING, result.last().state)
    }
}
