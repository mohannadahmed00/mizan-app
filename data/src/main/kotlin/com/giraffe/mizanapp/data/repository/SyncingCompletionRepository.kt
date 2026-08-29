package com.giraffe.mizanapp.data.repository

import androidx.room.withTransaction
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.OutboxEntry
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Decorates [RoomCompletionRepository] with the sync bookkeeping — enqueueing
 * a change is the only thing this class adds. **No import from
 * `RemoteDataSource.kt` or Ktor — this class has no network dependency at
 * all.** `record` and `undoLast` run inside one transaction with the local
 * write and the enqueue: either both land or neither does (FR-015), and
 * every read delegates unchanged.
 */
class SyncingCompletionRepository(
    private val delegate: RoomCompletionRepository,
    private val outbox: Outbox,
    private val accountScope: AccountScope,
    private val db: MizanDatabase,
) : CompletionRepository {

    override suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome = db.withTransaction {
        val outcome = delegate.record(date, taskSlug)
        val session = accountScope.current()
        if (outcome is RecordOutcome.Recorded && session is AccountSession.SignedIn) {
            enqueueCompletion(outcome.completion, session.userId)
            enqueueDayRecord(date, session.userId)
        }
        outcome
    }

    override suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome = db.withTransaction {
        val outcome = delegate.undoLast(date, taskSlug)
        val session = accountScope.current()
        if (outcome is UndoOutcome.Reversed && session is AccountSession.SignedIn) {
            enqueueCompletion(outcome.completion, session.userId)
        }
        outcome
    }

    private suspend fun enqueueCompletion(completion: Completion, userId: String) {
        val payload = Json.encodeToString(
            RemoteCompletion(
                id = completion.id,
                userId = userId,
                creditedDate = completion.creditedDate.toString(),
                taskSlug = completion.taskSlug,
                pointsAwarded = completion.pointsAwarded,
                recordedAt = completion.recordedAt.toString(),
                reversedAt = completion.reversedAt?.toString(),
            ),
        )
        outbox.enqueue(
            OutboxEntry(
                entityType = OutboxEntry.EntityType.COMPLETION,
                entityId = completion.id,
                operation = OutboxEntry.Operation.UPSERT,
                payload = payload,
            ),
        )
    }

    private suspend fun enqueueDayRecord(date: LocalDate, userId: String) {
        val plan = db.dayPlanDao().planByDate(date.toString()) ?: return
        val payload = Json.encodeToString(
            RemoteDayRecord(userId = userId, date = date.toString(), catalogueVersion = plan.plan.catalogueVersion),
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

    override fun observeCompletions(date: LocalDate): Flow<List<Completion>> = delegate.observeCompletions(date)

    override suspend fun liveCount(date: LocalDate, taskSlug: String): Int = delegate.liveCount(date, taskSlug)

    override suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion> =
        delegate.liveBetween(start, end)

    override fun observeConsistencyDates(): Flow<List<LocalDate>> = delegate.observeConsistencyDates()
}
