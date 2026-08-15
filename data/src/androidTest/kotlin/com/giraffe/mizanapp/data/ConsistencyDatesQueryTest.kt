package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsistencyDatesQueryTest : DbTestBase() {

    @Test
    fun empty_record_reports_no_dates() = runTest {
        seedAndPlanToday()

        assertEquals(emptyList<LocalDate>(), completions.observeConsistencyDates().first())
    }

    @Test
    fun one_date_appears_once_regardless_of_completion_count() = runTest {
        seedAndPlanToday()
        completions.record(time.today(), "fajr-1")
        completions.record(time.today(), "dhuhr-1")
        completions.record(time.today(), "dhuhr-2")

        val result = completions.observeConsistencyDates().first()

        assertEquals(listOf(time.today()), result)
        assertEquals(1, result.size)
    }

    @Test
    fun tombstoned_completion_leaves_the_date_uncounted() = runTest {
        seedAndPlanToday()
        completions.record(time.today(), "fajr-1")
        completions.undoLast(time.today(), "fajr-1")

        assertEquals(emptyList<LocalDate>(), completions.observeConsistencyDates().first())
    }

    @Test
    fun dates_come_back_ascending_across_days() = runTest {
        seedAndPlanToday()
        val first = time.today()
        completions.record(first, "fajr-1")

        time.setDate(first.plusDays(1))
        dayPlans.ensurePlanFor(time.today())
        val second = time.today()
        completions.record(second, "fajr-1")

        time.setDate(second.plusDays(1))
        dayPlans.ensurePlanFor(time.today())
        val third = time.today()
        completions.record(third, "fajr-1")

        assertEquals(listOf(first, second, third), completions.observeConsistencyDates().first())
    }
}
