package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * The one seam every surface reads sync state through.
 *
 * Status is derived, never stored: `deriveSyncStatus(session, pending, reachable,
 * coverage)` is a pure function in `:domain`, so the same inputs produce the same
 * status everywhere (Principle VII's "no second opinion", applied to status
 * rather than to time). `NotSyncing` is the strongest thing this interface can
 * say, and it says nothing about the user (Principle IX).
 */
interface SyncRepository {

    fun observeStatus(): Flow<SyncStatus>

    /** Changes not yet accepted by the account. Drives FR-007c's sign-out warning. */
    fun observePendingCount(): Flow<Int>

    /**
     * Requests a sync now. Returns immediately — never blocks an interaction (FR-014).
     * Idempotent: calling it during a run does not start a second one.
     */
    fun syncNow()
}
