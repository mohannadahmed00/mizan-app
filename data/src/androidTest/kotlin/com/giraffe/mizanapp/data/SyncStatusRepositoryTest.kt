package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.OutboxSyncRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.OutboxEntry
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusRepositoryTest : DbTestBase() {

    private val userId = "user-1"

    /**
     * [scope] is the caller's own `runBlocking` scope wherever `syncNow()` is
     * exercised, so the launched migration is a structured child the test
     * waits for before it (and `DbTestBase`'s teardown) returns — otherwise
     * the background coroutine can still be mid-transaction when the test's
     * `@After` closes the database.
     */
    private fun buildRepository(fake: FakeRemoteDataSource, scope: CoroutineScope): Pair<OutboxSyncRepository, Outbox> {
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        val engine = SyncEngine(db, outbox, accountScope, fake, catalogue, time)
        return OutboxSyncRepository(db, accountScope, engine, scope) to outbox
    }

    private suspend fun enqueueOne(outbox: Outbox) {
        val row = com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord(
            userId = userId,
            date = "2026-08-16",
            catalogueVersion = 1,
        )
        outbox.enqueue(
            OutboxEntry(
                entityType = OutboxEntry.EntityType.DAY_RECORD,
                entityId = "2026-08-16",
                operation = OutboxEntry.Operation.UPSERT,
                payload = kotlinx.serialization.json.Json.encodeToString(row),
            ),
        )
    }

    @Test
    fun observePendingCount_tracks_the_outbox_exactly() = runBlocking {
        val fake = FakeRemoteDataSource()
        val (repository, outbox) = buildRepository(fake, this)

        assertEquals(0, repository.observePendingCount().first())

        enqueueOne(outbox)

        assertEquals(1, repository.observePendingCount().first())
    }

    @Test
    fun observeStatus_is_NotSignedIn_while_signed_out() = runBlocking {
        val fake = FakeRemoteDataSource()
        val (repository, outbox) = buildRepository(fake, this)
        enqueueOne(outbox)

        assertEquals(SyncStatus.NotSignedIn, repository.observeStatus().first())
    }

    @Test
    fun observeStatus_is_Pending_with_a_queue_and_a_reachable_remote() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val (repository, outbox) = buildRepository(fake, this)
        AccountScope(db.accountScopeDao(), time).set(userId, "user@example.test", null)
        enqueueOne(outbox)

        val status = repository.observeStatus().first()
        assertTrue("expected Pending, got $status", status is SyncStatus.Pending)
        assertEquals(1, (status as SyncStatus.Pending).count)
    }

    @Test
    fun observeStatus_is_NotSyncing_with_a_queue_and_the_remote_unreachable() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId; unreachable = true }
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        val engine = SyncEngine(db, outbox, accountScope, fake, catalogue, time)
        val repository = OutboxSyncRepository(db, accountScope, engine, this)
        accountScope.set(userId, "user@example.test", null)
        enqueueOne(outbox)

        // Drive the engine directly and await it — this test is about the
        // status derived from an unreachable remote, not about syncNow's own
        // fire-and-forget scheduling (covered separately below).
        engine.drain()

        val status = repository.observeStatus().first()
        assertTrue("expected NotSyncing, got $status", status is SyncStatus.NotSyncing)
    }

    @Test
    fun observeStatus_is_UpToDate_on_an_empty_queue() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val (repository, _) = buildRepository(fake, this)
        AccountScope(db.accountScopeDao(), time).set(userId, "user@example.test", null)

        assertEquals(SyncStatus.UpToDate, repository.observeStatus().first())
    }

    @Test
    fun syncNow_returns_without_suspending_on_the_remote() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val (repository, outbox) = buildRepository(fake, this)
        AccountScope(db.accountScopeDao(), time).set(userId, "user@example.test", null)
        enqueueOne(outbox)

        val elapsed = System.nanoTime()
        repository.syncNow()
        val durationMs = (System.nanoTime() - elapsed) / 1_000_000

        assertTrue("syncNow must return immediately, took ${durationMs}ms", durationMs < 250)
    }
}
