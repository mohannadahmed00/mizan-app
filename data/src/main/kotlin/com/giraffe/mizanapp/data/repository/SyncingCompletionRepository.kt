package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

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

    override suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome {
        TODO("T080")
    }

    override suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome {
        TODO("T080")
    }

    override fun observeCompletions(date: LocalDate): Flow<List<Completion>> = delegate.observeCompletions(date)

    override suspend fun liveCount(date: LocalDate, taskSlug: String): Int = delegate.liveCount(date, taskSlug)

    override suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion> =
        delegate.liveBetween(start, end)

    override fun observeConsistencyDates(): Flow<List<LocalDate>> = delegate.observeConsistencyDates()
}
