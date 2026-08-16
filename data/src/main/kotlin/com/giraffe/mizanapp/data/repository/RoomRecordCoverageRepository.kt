package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Signed out, or signed in with `backfill_complete` set, this is always
 * `RecordCoverage.completeFrom(earliestPlanDate())` — the exact floor `003`
 * already used, so the offline product's behaviour is unchanged (R9). The
 * floor only moves backwards within a session; it never advances forwards as
 * a side effect of a read — this class never writes a cursor, only reads one.
 */
class RoomRecordCoverageRepository(
    private val db: MizanDatabase,
    private val accountScope: AccountScope,
) : RecordCoverageRepository {

    override fun observeCoverage(): Flow<RecordCoverage> =
        accountScope.observe().map { coverage() }

    override suspend fun coverage(): RecordCoverage {
        val session = accountScope.current()
        val backfillComplete = db.syncCursorDao().get(KEY_BACKFILL_COMPLETE) == "true"
        if (session !is AccountSession.SignedIn || backfillComplete) {
            return RecordCoverage.completeFrom(earliestPlanDate())
        }
        val floor = db.syncCursorDao().get(KEY_BACKFILL_FLOOR)?.let(LocalDate::parse)
        return RecordCoverage(knownFrom = floor, complete = false)
    }

    private suspend fun earliestPlanDate(): LocalDate? =
        db.dayPlanDao().earliestPlanDate()?.let(LocalDate::parse)

    private companion object {
        const val KEY_BACKFILL_COMPLETE = "backfill_complete"
        const val KEY_BACKFILL_FLOOR = "backfill_floor"
    }
}
