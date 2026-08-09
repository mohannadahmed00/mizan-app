package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompletionRepositoryTest : DbTestBase() {

    @Before
    fun seed() = runTest { seedAndPlanToday() }

    @Test
    fun recording_stores_the_planned_points() = runTest {
        val outcome = completions.record(time.today(), "fajr-1")

        assertTrue("$outcome", outcome is RecordOutcome.Recorded)
        assertEquals(2, (outcome as RecordOutcome.Recorded).completion.pointsAwarded)
        assertEquals(1, outcome.liveCount)
    }

    @Test
    fun adhkar_accepts_nine_refuses_the_tenth() = runTest {
        repeat(9) {
            assertTrue(completions.record(time.today(), "adhkar") is RecordOutcome.Recorded)
        }

        val tenth = completions.record(time.today(), "adhkar")

        assertTrue("$tenth", tenth is RecordOutcome.AtLimit)
        assertEquals(9, (tenth as RecordOutcome.AtLimit).limit)
        assertEquals(9, completions.liveCount(time.today(), "adhkar"))
    }

    @Test
    fun one_undo_frees_exactly_one_slot() = runTest {
        repeat(9) { completions.record(time.today(), "adhkar") }

        val undone = completions.undoLast(time.today(), "adhkar")
        assertTrue("$undone", undone is UndoOutcome.Reversed)
        assertEquals(8, completions.liveCount(time.today(), "adhkar"))

        assertTrue(completions.record(time.today(), "adhkar") is RecordOutcome.Recorded)
        assertEquals(9, completions.liveCount(time.today(), "adhkar"))
    }

    @Test
    fun undo_tombstones_rather_than_deletes() = runTest {
        completions.record(time.today(), "fajr-1")
        completions.undoLast(time.today(), "fajr-1")

        assertEquals("the row must still exist", 1, db.completionDao().countAllRows(time.today().toString()))
        assertEquals("but must not count", 0, completions.liveCount(time.today(), "fajr-1"))
    }

    @Test
    fun a_reversed_record_never_appears_in_the_observed_list() = runTest {
        completions.record(time.today(), "fajr-1")
        completions.undoLast(time.today(), "fajr-1")

        assertTrue(completions.observeCompletions(time.today()).first().isEmpty())
    }

    @Test
    fun undoing_nothing_is_harmless() = runTest {
        val outcome = completions.undoLast(time.today(), "fajr-1")

        assertTrue("$outcome", outcome is UndoOutcome.NothingToUndo)
    }

    /** SC-012: no sequence of undos may permanently reduce what a task can contribute. */
    @Test
    fun repeated_undo_and_redo_never_loses_a_slot() = runTest {
        repeat(9) { completions.record(time.today(), "adhkar") }

        repeat(10) { round ->
            completions.undoLast(time.today(), "adhkar")
            assertEquals("round $round", 8, completions.liveCount(time.today(), "adhkar"))
            completions.record(time.today(), "adhkar")
            assertEquals("round $round", 9, completions.liveCount(time.today(), "adhkar"))
        }
    }

    // --- FR-015: only the current date is writable -----------------------------

    @Test
    fun recording_against_yesterday_is_refused_and_writes_nothing() = runTest {
        val yesterday = time.today().minusDays(1)

        val outcome = completions.record(yesterday, "fajr-1")

        assertTrue("$outcome", outcome is RecordOutcome.NotWritable)
        assertEquals(0, db.completionDao().countAllRows(yesterday.toString()))
    }

    @Test
    fun undoing_against_yesterday_is_refused_and_reverses_nothing() = runTest {
        completions.record(time.today(), "fajr-1")
        val today = time.today()

        time.setDate(today.plusDays(1))
        val outcome = completions.undoLast(today, "fajr-1")

        assertTrue("$outcome", outcome is UndoOutcome.NotWritable)
        assertEquals("the earlier record must be untouched", 1, completions.liveCount(today, "fajr-1"))
    }
}
