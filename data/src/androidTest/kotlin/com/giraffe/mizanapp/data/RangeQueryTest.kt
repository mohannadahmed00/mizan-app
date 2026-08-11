package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Range reads over plans and completions — what the weekly sheet needs and
 * `002` never required. Every completion range read must exclude tombstones,
 * exactly like the single-date reads it sits beside.
 */
@RunWith(AndroidJUnit4::class)
class RangeQueryTest : DbTestBase() {

    @Test
    fun plans_between_returns_only_the_dates_that_have_plans_in_order() = runTest {
        catalogue.seedIfNeeded()
        time.setDate(LocalDate.parse("2026-08-08"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-08"))
        time.setDate(LocalDate.parse("2026-08-10"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-10"))
        time.setDate(LocalDate.parse("2026-08-12"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-12"))

        val plans = dayPlans.plansBetween(LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-14"))

        assertEquals(3, plans.size)
        assertEquals(
            listOf("2026-08-08", "2026-08-10", "2026-08-12"),
            plans.map { it.date.toString() },
        )
    }

    @Test
    fun the_range_is_inclusive_at_both_ends() = runTest {
        catalogue.seedIfNeeded()
        time.setDate(LocalDate.parse("2026-08-08"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-08"))

        val plans = dayPlans.plansBetween(LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-08"))

        assertEquals(1, plans.size)
    }

    @Test
    fun earliest_plan_date_is_the_record_start() = runTest {
        catalogue.seedIfNeeded()
        time.setDate(LocalDate.parse("2026-08-10"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-10"))
        time.setDate(LocalDate.parse("2026-08-08"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-08"))

        assertEquals(LocalDate.parse("2026-08-08"), dayPlans.earliestPlanDate())
    }

    @Test
    fun earliest_plan_date_is_null_when_nothing_exists() = runTest {
        assertNull(dayPlans.earliestPlanDate())
    }

    @Test
    fun a_reversed_completion_does_not_appear_in_a_range_read() = runTest {
        seedAndPlanToday()
        time.setDate(LocalDate.parse("2026-08-10"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-10"))
        completions.record(LocalDate.parse("2026-08-10"), "fajr-1")
        completions.undoLast(LocalDate.parse("2026-08-10"), "fajr-1")

        val live = completions.liveBetween(LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-14"))

        assertTrue("a tombstoned completion must not appear in a range read", live.isEmpty())
    }
}
