package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.data.sync.SyncScheduler
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import com.giraffe.mizanapp.domain.sync.SyncStatus
import com.giraffe.mizanapp.domain.sync.deriveSyncStatus
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val scheduler: SyncScheduler? = null,
) : SyncRepository {

    override fun observeStatus(): Flow<SyncStatus> =
        combine(accountScope.observe(), observePendingCount(), engine.reachable) { session, pending, reachable ->
            Triple(session, pending, reachable)
        }.map { (session, pending, reachable) ->
            val coverage = RecordCoverage.completeFrom(db.dayPlanDao().earliestPlanDate()?.let(LocalDate::parse))
            deriveSyncStatus(session, pending, reachable, coverage)
        }

    override fun observePendingCount(): Flow<Int> = db.outboxDao().observeCount()

    override fun syncNow() {
        scheduler?.schedule()
        scope.launch {
            val session = accountScope.current()
            if (session is AccountSession.SignedIn) engine.migrateOnSignIn(session.userId)
        }
    }
}
