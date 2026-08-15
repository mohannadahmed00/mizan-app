package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `005` SC-012: `002`'s `DayWritePolicy` already refuses writes against any
 * date but today. This increment re-asserts it because it is what makes
 * past days reachable in the first place - a regression here would be
 * `005`-caused even though the rule itself lives in `002`.
 */
@RunWith(AndroidJUnit4::class)
class PastDayNotWritableTest : DbTestBase() {

    @Test
    fun recording_against_an_elapsed_date_is_refused() = runTest {
        catalogue.seedIfNeeded()
        val pastDate = LocalDate.parse("2026-08-01")
        time.setDate(pastDate)
        dayPlans.ensurePlanFor(pastDate)
        time.setDate(LocalDate.parse("2026-08-15"))

        val before = completions.liveBetween(pastDate, pastDate)
        val outcome = completions.record(pastDate, "fajr-1")
        val after = completions.liveBetween(pastDate, pastDate)

        assertTrue("expected NotWritable, got $outcome", outcome is RecordOutcome.NotWritable)
        assertEquals(before, after)
    }

    @Test
    fun undoing_against_an_elapsed_date_is_refused() = runTest {
        catalogue.seedIfNeeded()
        val date = LocalDate.parse("2026-08-01")
        time.setDate(date)
        dayPlans.ensurePlanFor(date)
        completions.record(date, "fajr-1")
        time.setDate(LocalDate.parse("2026-08-15"))

        val before = completions.liveBetween(date, date)
        val outcome = completions.undoLast(date, "fajr-1")
        val after = completions.liveBetween(date, date)

        assertTrue("expected NotWritable, got $outcome", outcome is UndoOutcome.NotWritable)
        assertEquals("no completion may be reversed by this call", before, after)
    }
}
