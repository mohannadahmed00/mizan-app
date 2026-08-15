package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreakBackfillTest : DbTestBase() {

    @Test
    fun backfilled_plans_contribute_nothing_until_something_is_recorded() = runTest {
        catalogue.seedIfNeeded()
        time.setDate(LocalDate.parse("2026-03-21"))

        // Advance a week without opening the app, backfilling each elapsed
        // date exactly as GetWeekSummary does — no completion is created.
        val start = LocalDate.parse("2026-03-15")
        (0..6).forEach { offset -> dayPlans.ensurePlanFor(start.plusDays(offset.toLong())) }
        dayPlans.ensurePlanFor(time.today())

        assertEquals(emptyList<LocalDate>(), completions.observeConsistencyDates().first())

        completions.record(time.today(), "fajr-1")

        assertEquals(listOf(time.today()), completions.observeConsistencyDates().first())
    }
}
