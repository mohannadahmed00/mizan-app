package com.giraffe.mizanapp.domain.sync

import java.time.LocalDate

/**
 * What the user is told about sync, and the most this type is allowed to say.
 *
 * **There is no failure state here, by construction** (Principle IX, FR-022).
 * [NotSyncing] is the strongest member, and it is a fact about reachability, not
 * about the person: retrying is automatic and unbounded (FR-021a), so asking the
 * user to fix it would make an infrastructure condition their responsibility.
 */
sealed interface SyncStatus {

    /** Signed out. Renders nothing at all — the offline product shows no sync surface (FR-004). */
    data object NotSignedIn : SyncStatus

    data object UpToDate : SyncStatus

    /** A count of changes waiting, never styled as a warning. */
    data class Pending(val count: Int) : SyncStatus

    /** Signed in, account unreachable. Recording is unaffected (FR-021). */
    data object NotSyncing : SyncStatus

    /**
     * This device is still fetching the account's earlier history. Outranks
     * [Pending] because a user whose history is still arriving needs to know
     * that before anything else (FR-023b).
     */
    data class LoadingEarlierDays(val knownFrom: LocalDate?) : SyncStatus
}
