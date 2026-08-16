package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import kotlinx.coroutines.flow.Flow

/**
 * Signed out, or signed in with `backfill_complete` set, this is always
 * `RecordCoverage.completeFrom(earliestPlanDate())` — the exact floor `003`
 * already used, so the offline product's behaviour is unchanged (R9). The
 * floor only moves backwards within a session; it never advances forwards as
 * a side effect of a read.
 */
class RoomRecordCoverageRepository(
    private val db: MizanDatabase,
    private val accountScope: AccountScope,
) : RecordCoverageRepository {

    override fun observeCoverage(): Flow<RecordCoverage> {
        TODO("T102")
    }

    override suspend fun coverage(): RecordCoverage {
        TODO("T102")
    }
}
