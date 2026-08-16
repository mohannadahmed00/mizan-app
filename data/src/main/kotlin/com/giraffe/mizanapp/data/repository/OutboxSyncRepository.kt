package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/**
 * [SyncRepository] over the [SyncEngine]'s outbox and reachability.
 *
 * `syncNow()` launches the engine on an application-scoped coroutine and
 * returns immediately — nothing on the recording path ever awaits it
 * (FR-014). Status is derived, never stored: `deriveSyncStatus` is the single
 * pure function every input here feeds. Coverage is
 * `RecordCoverage.completeFrom(earliestPlanDate())` until T102 adds real
 * backfill tracking — exactly the floor `003` already used, so signed-out and
 * pre-backfill behaviour is unchanged.
 */
class OutboxSyncRepository(
    private val db: MizanDatabase,
    private val accountScope: AccountScope,
    private val engine: SyncEngine,
) : SyncRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeStatus(): Flow<SyncStatus> {
        TODO("T067")
    }

    override fun observePendingCount(): Flow<Int> = db.outboxDao().observeCount()

    override fun syncNow() {
        TODO("T067")
    }
}
