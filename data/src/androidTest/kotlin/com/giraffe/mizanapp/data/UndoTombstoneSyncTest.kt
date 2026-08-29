package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.SyncingCompletionRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An undo is a tombstone carried by an upsert, never a delete (FR-018, US2
 * AS3). A device that only ever sees the tombstone must not resurrect the
 * completion as live.
 */
class UndoTombstoneSyncTest : DbTestBase() {

    private val userId = "user-1"

    @Test
    fun undo_syncs_as_a_tombstone_and_never_as_a_delete() = runBlocking {
        seedAndPlanToday()
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        accountScope.set(userId, "user@example.test", null)
        val engine = SyncEngine(db, outbox, accountScope, fake, catalogue, time)
        val syncing = SyncingCompletionRepository(completions, outbox, accountScope, db)

        val recorded = syncing.record(time.today(), "fajr-1")
        assertTrue(recorded is RecordOutcome.Recorded)
        val id = (recorded as RecordOutcome.Recorded).completion.id
        engine.drain()

        val reversed = syncing.undoLast(time.today(), "fajr-1")
        assertTrue(reversed is UndoOutcome.Reversed)
        engine.drain()

        val (_, remoteCompletions) = fake.rows()
        assertEquals(1, remoteCompletions.size)
        val row = remoteCompletions.single { it.id == id }
        assertNotNull("the remote row must carry a non-null reversed_at", row.reversedAt)

        // Apply what the remote holds onto a second, independent device.
        val secondDbName = "mizan-second-device.db"
        context.deleteDatabase(secondDbName)
        val secondDb = androidx.room.Room.databaseBuilder(context, com.giraffe.mizanapp.data.db.MizanDatabase::class.java, secondDbName).build()
        try {
            secondDb.completionDao().insertIgnoring(
                com.giraffe.mizanapp.data.db.entities.CompletionEntity(
                    id = row.id,
                    dayPlanId = "plan-placeholder",
                    taskSlug = row.taskSlug,
                    creditedDate = row.creditedDate,
                    pointsAwarded = row.pointsAwarded,
                    recordedAt = java.time.Instant.parse(row.recordedAt).toEpochMilli(),
                    reversedAt = row.reversedAt?.let { java.time.Instant.parse(it).toEpochMilli() },
                    updatedAt = time.now().toEpochMilli(),
                    userId = userId,
                ),
            )
            val liveOnSecondDevice = secondDb.completionDao().liveByDate(row.creditedDate)
            assertTrue("the completion must not reappear as live", liveOnSecondDevice.none { it.id == row.id })
        } finally {
            secondDb.close()
            context.deleteDatabase(secondDbName)
        }
    }
}
