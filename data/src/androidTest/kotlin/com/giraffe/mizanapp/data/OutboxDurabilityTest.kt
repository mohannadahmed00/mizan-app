package com.giraffe.mizanapp.data

import androidx.room.Room
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.OutboxEntry
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A queued change is the only copy of a fact the user believes is recorded —
 * nothing may expire, evict, or cap it, however long the device stays
 * offline (FR-015, FR-021a, SC-010).
 */
class OutboxDurabilityTest : DbTestBase() {

    private val userId = "user-1"
    private val entryCount = 25_000

    private fun entry(index: Int): OutboxEntry {
        val date = LocalDate.of(2020, 1, 1).plusDays(index.toLong())
        val payload = Json.encodeToString(RemoteDayRecord(userId = userId, date = date.toString(), catalogueVersion = 1))
        return OutboxEntry(
            entityType = OutboxEntry.EntityType.DAY_RECORD,
            entityId = date.toString(),
            operation = OutboxEntry.Operation.UPSERT,
            payload = payload,
        )
    }

    @Test
    fun a_years_worth_of_entries_survives_reopen_restart_and_a_year_offline_then_drains() = runBlocking {
        val outbox = Outbox(db, time)
        for (i in 0 until entryCount) outbox.enqueue(entry(i))
        assertEquals(entryCount, outbox.due(time.now(), limit = Int.MAX_VALUE).size)

        // Close and reopen — the cheap proxy for process death (SC-005's own convention).
        openDatabase(keepTime = true)
        val reopenedOutbox = Outbox(db, time)
        assertEquals(entryCount, reopenedOutbox.due(time.now(), limit = Int.MAX_VALUE).size)

        // Additionally: close, delete nothing, and rebuild the whole object graph
        // exactly as a device restart would — a fresh Room instance over the same file.
        // Reassigning the inherited `db` lets DbTestBase's own teardown manage it.
        db.close()
        db = Room.databaseBuilder(context, MizanDatabase::class.java, TEST_DB_NAME).build()
        val rebuiltDb = db
        val rebuiltOutbox = Outbox(rebuiltDb, time)
        assertEquals(entryCount, rebuiltOutbox.due(time.now(), limit = Int.MAX_VALUE).size)

        // A year offline: nothing expires, is evicted, or is capped.
        time.advanceBy(Duration.ofDays(365))
        assertEquals(entryCount, rebuiltOutbox.due(time.now(), limit = Int.MAX_VALUE).size)

        // Reachable again: the whole queue drains.
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val accountScope = AccountScope(rebuiltDb.accountScopeDao(), time)
        val engine = SyncEngine(rebuiltDb, rebuiltOutbox, accountScope, fake, time)
        var guard = 0
        while (rebuiltOutbox.due(time.now(), limit = 1).isNotEmpty() && guard < entryCount / 200 + 5) {
            engine.drain()
            guard++
        }
        assertEquals(0, rebuiltOutbox.due(time.now(), limit = 1).size)
        assertEquals(entryCount, fake.rows().first.size)
    }

    private companion object {
        const val TEST_DB_NAME = "mizan-test.db"
    }
}
