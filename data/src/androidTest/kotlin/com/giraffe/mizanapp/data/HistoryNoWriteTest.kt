package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.HistoryOutcome
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `005` SC-008: scrolling the entire history of a record changes the stored
 * record in no way at all. This is what clarification Q2 decided — a
 * continuous list back to the record start would otherwise turn a scroll
 * into thousands of writes.
 */
@RunWith(AndroidJUnit4::class)
class HistoryNoWriteTest : DbTestBase() {

    @Test
    fun scrolling_the_whole_record_changes_nothing_stored() = runTest {
        catalogue.seedIfNeeded()

        // Two anchor plans, twelve weeks apart, with nothing recorded between them -
        // exactly the "twelve empty weeks" shape clarification Q1 requires history to show.
        val recordStart = LocalDate.parse("2026-05-30")
        val currentWeekStart = LocalDate.parse("2026-08-15")
        time.setDate(recordStart)
        dayPlans.ensurePlanFor(recordStart)
        time.setDate(currentWeekStart)
        dayPlans.ensurePlanFor(currentWeekStart)

        val plansBefore = db.dayPlanDao().countPlans()

        val useCase = GetHistoryPage(dayPlans, completions, catalogue, time, coverageRepo)
        var cursor: com.giraffe.mizanapp.domain.week.WeekKey? = null
        var hasMore: Boolean
        do {
            val outcome = useCase(before = cursor) as HistoryOutcome.Ready
            cursor = outcome.page.oldestLoaded
            hasMore = outcome.page.hasMore
        } while (hasMore)

        val plansAfter = db.dayPlanDao().countPlans()
        assertEquals("scrolling must create no plan rows", plansBefore, plansAfter)
    }
}
