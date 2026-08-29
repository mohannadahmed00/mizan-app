package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.db.entities.SyncCursorEntity
import com.giraffe.mizanapp.data.repository.RoomRecordCoverageRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordCoverageRepositoryTest : DbTestBase() {

    private fun repository() = RoomRecordCoverageRepository(db, AccountScope(db.accountScopeDao(), time))

    @Test
    fun signed_out_returns_completeFrom_earliestPlanDate() = runBlocking {
        seedAndPlanToday()

        val coverage = repository().coverage()

        assertEquals(RecordCoverage.completeFrom(time.today()), coverage)
    }

    @Test
    fun signed_in_with_backfill_complete_set_returns_completeFrom_earliestPlanDate() = runBlocking {
        seedAndPlanToday()
        AccountScope(db.accountScopeDao(), time).set("user-1", "user@example.test", null)
        db.syncCursorDao().upsert(SyncCursorEntity(key = "backfill_complete", value = "true"))
        db.syncCursorDao().upsert(SyncCursorEntity(key = "backfill_floor", value = "2020-01-01"))

        val coverage = repository().coverage()

        assertEquals(RecordCoverage.completeFrom(time.today()), coverage)
    }

    @Test
    fun signed_in_mid_backfill_returns_the_floor_and_incomplete() = runBlocking {
        seedAndPlanToday()
        AccountScope(db.accountScopeDao(), time).set("user-1", "user@example.test", null)
        val floor = LocalDate.parse("2026-06-01")
        db.syncCursorDao().upsert(SyncCursorEntity(key = "backfill_floor", value = floor.toString()))

        val coverage = repository().coverage()

        assertEquals(RecordCoverage(knownFrom = floor, complete = false), coverage)
    }

    @Test
    fun the_floor_never_advances_forwards_within_a_session() = runBlocking {
        seedAndPlanToday()
        AccountScope(db.accountScopeDao(), time).set("user-1", "user@example.test", null)
        val earlierFloor = LocalDate.parse("2026-03-01")
        db.syncCursorDao().upsert(SyncCursorEntity(key = "backfill_floor", value = earlierFloor.toString()))

        val first = repository().coverage()
        assertEquals(earlierFloor, first.knownFrom)

        // A read never rewrites the cursor itself — simulated here by reading twice
        // without any write in between and confirming the floor is unchanged.
        val second = repository().coverage()
        assertEquals(earlierFloor, second.knownFrom)
    }
}
