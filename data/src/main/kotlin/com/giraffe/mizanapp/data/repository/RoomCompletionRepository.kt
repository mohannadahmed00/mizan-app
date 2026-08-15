package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.mapper.toDomain
import com.giraffe.mizanapp.data.mapper.toEntity
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The append-only completion log.
 *
 * Two guarantees carry most of this class's weight:
 *
 * 1. **The write policy is consulted first, before any storage is touched**
 *    (FR-015). A policy that is never called is decoration.
 * 2. **Every count filters out tombstones.** Undo reverses rather than deletes,
 *    so counting all rows would lock a nine-occurrence task at nine after one
 *    mistaken tap — permanently, and with no visible reason.
 */
class RoomCompletionRepository(
    private val database: MizanDatabase,
    private val dayPlans: DayPlanRepository,
    private val policy: DayWritePolicy,
    private val time: TimeProvider,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : CompletionRepository {

    override suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome {
        if (!policy.isWritable(date)) {
            return RecordOutcome.NotWritable(policy.refusalReason(date))
        }

        val plan = dayPlans.planFor(date)
            ?: return RecordOutcome.NotWritable("no plan exists for $date")
        val task = plan.plannedTasks.firstOrNull { it.taskSlug == taskSlug }
            ?: return RecordOutcome.NotWritable("$taskSlug does not apply on $date")

        val dao = database.completionDao()
        val live = dao.liveCount(date.toString(), taskSlug)
        if (live >= task.maxOccurrencesPerDay) {
            return RecordOutcome.AtLimit(task.maxOccurrencesPerDay)
        }

        val now = time.now()
        val completion = Completion(
            id = newId(),
            dayPlanId = plan.id,
            taskSlug = taskSlug,
            creditedDate = date,
            // The points the task was worth on this day, from the frozen plan —
            // never from the live catalogue (FR-011).
            pointsAwarded = task.points,
            recordedAt = now,
        )
        dao.insert(completion.toEntity(now.toEpochMilli()))

        return RecordOutcome.Recorded(completion, live + 1)
    }

    override suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome {
        if (!policy.isWritable(date)) {
            return UndoOutcome.NotWritable(policy.refusalReason(date))
        }

        val dao = database.completionDao()
        val latest = dao.latestLive(date.toString(), taskSlug) ?: return UndoOutcome.NothingToUndo

        val now = time.now()
        dao.reverseById(latest.id, now.toEpochMilli())

        val reversed = latest.toDomain().copy(reversedAt = now)
        return UndoOutcome.Reversed(reversed, dao.liveCount(date.toString(), taskSlug))
    }

    override fun observeCompletions(date: LocalDate): Flow<List<Completion>> =
        database.completionDao().observeLiveByDate(date.toString())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun liveCount(date: LocalDate, taskSlug: String): Int =
        database.completionDao().liveCount(date.toString(), taskSlug)

    override suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion> =
        database.completionDao().liveBetween(start.toString(), end.toString()).map { it.toDomain() }

    override fun observeConsistencyDates(): Flow<List<LocalDate>> =
        database.completionDao().observeLiveDates().map { dates -> dates.map(LocalDate::parse) }
}
