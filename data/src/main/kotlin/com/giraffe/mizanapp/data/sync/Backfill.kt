package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.SyncCursorEntity
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.LocalDate

/**
 * First sign-in on a device, and any resumption of it —
 * `specs/007-identity-cloud-sync/contracts/sync-engine.md` §4.
 *
 * Two resumable units of work: the head pull (this week, so the app is usable
 * immediately — SC-006, FR-023a) and a loop of descending 90-day pages, each
 * applied through [SyncEngine.applyRemote] and followed by a `backfill_floor`
 * commit, so an interruption costs at most one page (FR-023c). Every write
 * inside is an upsert, so re-applying a page after a restart is harmless —
 * the caller ([SyncWorker]) decides when to call [nextPage] again, this class
 * only ever does one page per call.
 */
class Backfill(
    private val db: MizanDatabase,
    private val engine: SyncEngine,
    private val remote: RemoteDataSource,
    private val time: TimeProvider,
) {

    /**
     * The single entry point [SyncWorker] chains: the head pull if backfill
     * has not started on this device yet, otherwise the next page. A no-op
     * once `backfill_complete` is set.
     */
    suspend fun resumeOnePage(): BackfillStep {
        if (db.syncCursorDao().get(KEY_COMPLETE) == "true") return BackfillStep.Complete
        return if (db.syncCursorDao().get(KEY_FLOOR) == null) headPull() else nextPage()
    }

    /** `recordsBetween(currentWeekStart, today)`, applied in one transaction. */
    suspend fun headPull(): BackfillStep {
        val today = time.today()
        val weekStart = WeekBoundary.startOfWeek(today)

        val headChanges = when (val result = remote.recordsBetween(weekStart, today)) {
            is RemoteResult.Ok -> result.value
            else -> return result.toStep()
        }
        engine.applyRemote(headChanges)

        val earliest = when (val result = remote.earliestRecordedDate()) {
            is RemoteResult.Ok -> result.value
            else -> return result.toStep()
        }

        db.syncCursorDao().upsert(SyncCursorEntity(KEY_FLOOR, weekStart.toString()))
        db.syncCursorDao().upsert(SyncCursorEntity(KEY_TARGET, earliest?.toString().orEmpty()))
        if (earliest == null || !weekStart.isAfter(earliest)) {
            db.syncCursorDao().upsert(SyncCursorEntity(KEY_COMPLETE, "true"))
        }
        return BackfillStep.HeadPulled
    }

    /**
     * One descending [PAGE_SIZE_DAYS]-day page ending the day before the
     * current `backfill_floor`. A no-op returning [BackfillStep.Complete] once
     * `backfill_complete` is already set, and safe to call again from a fresh
     * instance after any interruption — the floor persisted by the last
     * successful call is where this resumes, never re-fetching a page already
     * committed. The account's earliest date is read once, by [headPull], and
     * cached in `backfill_target` rather than re-queried per page.
     */
    suspend fun nextPage(): BackfillStep {
        if (db.syncCursorDao().get(KEY_COMPLETE) == "true") return BackfillStep.Complete
        val floor = db.syncCursorDao().get(KEY_FLOOR)?.let(LocalDate::parse) ?: return BackfillStep.Complete
        val target = db.syncCursorDao().get(KEY_TARGET)?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        if (target == null) {
            db.syncCursorDao().upsert(SyncCursorEntity(KEY_COMPLETE, "true"))
            return BackfillStep.Complete
        }

        val to = floor.minusDays(1)
        val naiveFrom = to.minusDays(PAGE_SIZE_DAYS - 1)
        val from = if (naiveFrom.isBefore(target)) target else naiveFrom

        val page = when (val result = remote.recordsBetween(from, to)) {
            is RemoteResult.Ok -> result.value
            else -> return result.toStep()
        }
        engine.applyRemote(page)

        db.syncCursorDao().upsert(SyncCursorEntity(KEY_FLOOR, from.toString()))
        if (!naiveFrom.isAfter(target)) {
            db.syncCursorDao().upsert(SyncCursorEntity(KEY_COMPLETE, "true"))
        }
        return BackfillStep.PageApplied(from, to)
    }

    private fun RemoteResult<*>.toStep(): BackfillStep = when (this) {
        is RemoteResult.Ok -> error("Ok is handled by the caller before reaching toStep()")
        RemoteResult.Unreachable, RemoteResult.NotAuthenticated, is RemoteResult.Rejected -> BackfillStep.Unreachable
    }

    private companion object {
        const val PAGE_SIZE_DAYS = 90L
        const val KEY_FLOOR = "backfill_floor"
        const val KEY_COMPLETE = "backfill_complete"
        const val KEY_TARGET = "backfill_target"
    }
}

sealed interface BackfillStep {
    data object HeadPulled : BackfillStep
    data class PageApplied(val from: LocalDate, val to: LocalDate) : BackfillStep
    data object Complete : BackfillStep
    data object Unreachable : BackfillStep
}
