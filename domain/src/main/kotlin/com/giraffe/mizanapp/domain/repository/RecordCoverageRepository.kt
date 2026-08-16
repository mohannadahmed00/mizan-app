package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.sync.RecordCoverage
import kotlinx.coroutines.flow.Flow

/**
 * How far back this device knows the account's record.
 *
 * Signed out, or signed in with backfill finished, this is always
 * `RecordCoverage.completeFrom(earliestPlanDate())` — the exact floor `003`
 * already uses, so the offline product's behaviour is unchanged. `complete =
 * false` never means "empty": a date below `knownFrom` is not yet known, never
 * 0%, never untouched, never absent (FR-023b). The floor only moves backwards
 * within a session; it never advances forwards as a side effect of a read.
 */
interface RecordCoverageRepository {

    fun observeCoverage(): Flow<RecordCoverage>

    suspend fun coverage(): RecordCoverage
}
