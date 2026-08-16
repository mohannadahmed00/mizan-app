package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.sync.Backfill
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FR-023a-c, SC-006: the head pull makes the current week usable immediately;
 * the page loop behind it is resumable at any point, at most one page's work
 * lost to an interruption, and never re-fetches or duplicates a page it has
 * already committed.
 */
@RunWith(AndroidJUnit4::class)
class BackfillResumeTest : DbTestBase() {

    private val userId = "user-1"

    @Test
    fun an_interrupted_backfill_resumes_without_refetching_or_duplicating_and_completes() = runTest {
        catalogue.seedIfNeeded()
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }

        val today = time.today()
        val earliest = today.minusDays(399)
        for (offset in 0L..399L) {
            val date = earliest.plusDays(offset)
            fake.upsertDayRecords(
                listOf(RemoteDayRecord(userId = userId, date = date.toString(), catalogueVersion = 1)),
            )
        }

        fun backfill() = Backfill(db, fake, time)

        backfill().headPull()

        val currentWeek = WeekBoundary.weekContaining(today)
        for (date in currentWeek.dates) {
            assertTrue("$date of the current week must exist after the head pull", dayPlans.planFor(date) != null)
        }

        val floors = mutableListOf<LocalDate>()

        // Page 1, then "interrupt" — every call below builds a fresh Backfill
        // over the same database, simulating a process restart that only has
        // the persisted `backfill_floor` to resume from.
        backfill().nextPage()
        floors += currentFloor()

        // Resume for page 2.
        backfill().nextPage()
        floors += currentFloor()

        // Page 3, then interrupt again.
        backfill().nextPage()
        floors += currentFloor()

        // Resume for page 4: must fetch exactly the next page, never repeat page 3.
        val readsBeforeResume = fake.readCount
        backfill().nextPage()
        floors += currentFloor()
        assertEquals(
            "resuming after an interruption must fetch exactly the next page, never re-fetch one already applied",
            readsBeforeResume + 1,
            fake.readCount,
        )

        // Interrupt once more, then drain however many pages remain to completion.
        var guard = 0
        while (db.syncCursorDao().get("backfill_complete") != "true") {
            backfill().nextPage()
            floors += currentFloor()
            guard++
            assertTrue("backfill did not complete within a sane number of pages", guard < 10)
        }

        for (i in 1 until floors.size) {
            assertTrue("backfill_floor must move strictly backwards page over page", floors[i].isBefore(floors[i - 1]))
        }

        assertEquals("true", db.syncCursorDao().get("backfill_complete"))
        for (offset in 0L..399L) {
            val date = earliest.plusDays(offset)
            assertTrue("$date must be present once the backfill is complete", dayPlans.planFor(date) != null)
        }
    }

    private suspend fun currentFloor(): LocalDate =
        LocalDate.parse(requireNotNull(db.syncCursorDao().get("backfill_floor")) { "backfill_floor must be committed after every page" })
}
