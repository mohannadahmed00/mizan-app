package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.domain.repository.CataloguePublicationRepository
import com.giraffe.mizanapp.domain.repository.PullOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider

/**
 * Pulls administrator-published catalogue versions from the account and adds
 * any this app both understands and does not already hold.
 *
 * Insert-only by construction: a version already present in `catalogue_versions`
 * is filtered out before it ever reaches a write — there is no update and no
 * delete path in this class at all (R10, FR-024a).
 */
class RemoteCataloguePublicationRepository(
    private val remote: RemoteDataSource,
    private val database: MizanDatabase,
    private val time: TimeProvider,
) : CataloguePublicationRepository {

    override suspend fun pullIfNewer(): PullOutcome = TODO("T113")
}
