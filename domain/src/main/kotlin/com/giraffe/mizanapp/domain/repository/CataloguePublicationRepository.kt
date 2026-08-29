package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect

/**
 * Pulls the administrator-published catalogue from the account.
 *
 * Insert-only: there is no method here, and no DAO method beneath it, that can
 * change a version already stored — which is what makes FR-026 and SC-009
 * structural rather than aspirational. `Unreachable` and `Rejected` both leave
 * the built-in seed in place and the app fully usable (FR-025).
 */
interface CataloguePublicationRepository {

    /**
     * Pulls published catalogue versions and inserts any this app understands and does not
     * already hold. Never alters, replaces, or removes a version already present (R10).
     *
     * A publication whose format this app cannot read is skipped in favour of the newest
     * one it can (FR-028) — never a crash, never a partial write.
     */
    suspend fun pullIfNewer(): PullOutcome
}

sealed interface PullOutcome {
    data class Added(val versions: List<Int>) : PullOutcome
    data object NothingNew : PullOutcome
    data class Skipped(val unreadableVersions: List<Int>) : PullOutcome
    data object Unreachable : PullOutcome
    data class Rejected(val defects: List<CatalogueDefect>) : PullOutcome
}
