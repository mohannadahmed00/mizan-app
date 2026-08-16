package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.LocalDate

/**
 * First sign-in on a device, and any resumption of it —
 * `specs/007-identity-cloud-sync/contracts/sync-engine.md` §4.
 *
 * Two resumable units of work: the head pull (this week, so the app is usable
 * immediately — SC-006, FR-023a) and a loop of descending 90-day pages, each
 * committed together with its floor in one transaction so an interruption
 * costs at most one page (FR-023c). Every write inside is an upsert, so
 * re-applying a page after a restart is harmless — the caller ([SyncWorker])
 * decides when to call [nextPage] again, this class only ever does one page
 * per call.
 */
class Backfill(
    private val db: MizanDatabase,
    private val remote: RemoteDataSource,
    private val time: TimeProvider,
) {

    /** `recordsBetween(currentWeekStart, today)`, applied in one transaction. */
    suspend fun headPull(): BackfillStep = TODO("T105")

    /**
     * One descending [PAGE_SIZE_DAYS]-day page ending the day before the
     * current `backfill_floor`. A no-op returning [BackfillStep.Complete] once
     * `backfill_complete` is already set, and safe to call again from a fresh
     * instance after any interruption — the floor persisted by the last
     * successful call is where this resumes, never re-fetching a page already
     * committed.
     */
    suspend fun nextPage(): BackfillStep = TODO("T105")

    private companion object {
        const val PAGE_SIZE_DAYS = 90L
    }
}

sealed interface BackfillStep {
    data object HeadPulled : BackfillStep
    data class PageApplied(val from: LocalDate, val to: LocalDate) : BackfillStep
    data object Complete : BackfillStep
    data object Unreachable : BackfillStep
}
