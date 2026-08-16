package com.giraffe.mizanapp.domain.sync

import com.giraffe.mizanapp.domain.identity.AccountSession

/**
 * The single place the sync status is decided.
 *
 * Pure, so the same inputs give the same status on every surface — Principle
 * VII's "no second opinion" rule, applied to status rather than to time. Six
 * ViewModels each holding their own version of this is exactly the drift it
 * warns about.
 *
 * Precedence, in order:
 *  1. signed out                     -> [SyncStatus.NotSignedIn]
 *  2. coverage incomplete            -> [SyncStatus.LoadingEarlierDays]
 *  3. changes pending, reachable     -> [SyncStatus.Pending]
 *  4. changes pending, unreachable   -> [SyncStatus.NotSyncing]
 *  5. otherwise                      -> [SyncStatus.UpToDate]
 *
 * Backfill outranks pending because a user whose history is still arriving needs
 * to know that before anything else (FR-023b). No branch can produce a failure,
 * a blame, or a red state — none exists to produce (Principle IX).
 */
fun deriveSyncStatus(
    session: AccountSession,
    pendingCount: Int,
    reachable: Boolean,
    coverage: RecordCoverage,
): SyncStatus {
    TODO("T024")
}
