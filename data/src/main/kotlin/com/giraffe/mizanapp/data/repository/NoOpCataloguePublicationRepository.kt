package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.domain.repository.CataloguePublicationRepository
import com.giraffe.mizanapp.domain.repository.PullOutcome

/**
 * Placeholder binding until T113 adds the real remote-backed implementation.
 * Reports nothing new — the built-in seeded catalogue stays in force, exactly
 * the behaviour FR-025 requires of an unreachable pull.
 */
class NoOpCataloguePublicationRepository : CataloguePublicationRepository {
    override suspend fun pullIfNewer(): PullOutcome = PullOutcome.NothingNew
}
