package com.giraffe.mizanapp.data.repository

import androidx.room.withTransaction
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.OutboxEntry
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Decorates [RoomDayPlanRepository] with the sync bookkeeping, in the same
 * shape as [SyncingCompletionRepository]: no network dependency, every read
 * delegates unchanged, and the enqueue happens in the same transaction as the
 * write it follows.
 */
class SyncingDayPlanRepository(
    private val delegate: RoomDayPlanRepository,
    private val outbox: Outbox,
    private val accountScope: AccountScope,
    private val db: MizanDatabase,
) : DayPlanRepository {

    override suspend fun planFor(date: LocalDate): DayPlan? = delegate.planFor(date)

    override suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome = db.withTransaction {
        val outcome = delegate.ensurePlanFor(date)
        val session = accountScope.current()
        if (outcome is EnsureOutcome.Created && session is AccountSession.SignedIn) {
            val payload = Json.encodeToString(
                RemoteDayRecord(
                    userId = session.userId,
                    date = date.toString(),
                    catalogueVersion = outcome.plan.catalogueVersion,
                ),
            )
            outbox.enqueue(
                OutboxEntry(
                    entityType = OutboxEntry.EntityType.DAY_RECORD,
                    entityId = date.toString(),
                    operation = OutboxEntry.Operation.UPSERT,
                    payload = payload,
                ),
            )
        }
        outcome
    }

    override fun observePlan(date: LocalDate): Flow<DayPlan?> = delegate.observePlan(date)

    override suspend fun plansBetween(start: LocalDate, end: LocalDate): List<DayPlan> = delegate.plansBetween(start, end)

    override suspend fun earliestPlanDate(): LocalDate? = delegate.earliestPlanDate()
}
