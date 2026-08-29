package com.giraffe.mizanapp.data.repository

import androidx.room.withTransaction
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.SectionEntity
import com.giraffe.mizanapp.data.db.entities.TaskDefinitionEntity
import com.giraffe.mizanapp.data.mapper.toEntity
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect
import com.giraffe.mizanapp.domain.catalogue.parseCatalogue
import com.giraffe.mizanapp.domain.catalogue.validateCatalogueContent
import com.giraffe.mizanapp.domain.repository.CataloguePublicationRepository
import com.giraffe.mizanapp.domain.repository.PullOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.util.UUID

/**
 * Pulls administrator-published catalogue versions from the account and adds
 * any this app both understands and does not already hold.
 *
 * Insert-only by construction: a version already present in `catalogue_versions`
 * is filtered out before it ever reaches a write — there is no update and no
 * delete path in this class at all (R10, FR-024a). A publication in a format
 * this app cannot read is skipped in favour of the newest one it can (FR-028).
 * A payload that fails validation is rejected wholesale — nothing from this
 * pull is written (FR-011, FR-019, FR-027).
 */
class RemoteCataloguePublicationRepository(
    private val remote: RemoteDataSource,
    private val database: MizanDatabase,
    private val time: TimeProvider,
) : CataloguePublicationRepository {

    override suspend fun pullIfNewer(): PullOutcome =
        when (val result = remote.catalogues(setOf(FORMAT_VERSION))) {
            is RemoteResult.Ok -> process(result.value)
            // Reads never fail the caller here — the built-in seed simply stays
            // in force (FR-025). A drain elsewhere is what actually signs the
            // user out on a dead session; this repository has no such seam.
            RemoteResult.Unreachable -> PullOutcome.Unreachable
            RemoteResult.NotAuthenticated -> PullOutcome.Unreachable
            is RemoteResult.Rejected -> PullOutcome.Unreachable
        }

    private suspend fun process(publications: List<RemotePublication>): PullOutcome {
        val unreadable = publications.filter { it.formatVersion != FORMAT_VERSION }.map { it.version }
        val readable = publications.filter { it.formatVersion == FORMAT_VERSION }

        val knownLocally = database.catalogueDao().versions().map { it.version }.toSet()
        val candidates = readable.filter { it.version !in knownLocally }

        if (candidates.isEmpty()) {
            return if (unreadable.isNotEmpty()) PullOutcome.Skipped(unreadable) else PullOutcome.NothingNew
        }

        val parsed = mutableListOf<Catalogue>()
        val defects = mutableListOf<CatalogueDefect>()
        for (candidate in candidates) {
            val candidateDefects = validateCatalogueContent(
                raw = candidate.payload,
                source = "remote:${candidate.version}",
            )
            if (candidateDefects.isNotEmpty()) {
                defects += candidateDefects
                continue
            }
            parseCatalogue(candidate.payload).getOrNull()?.let { parsed += it }
        }
        if (defects.isNotEmpty()) return PullOutcome.Rejected(defects)

        applyNew(parsed)
        return PullOutcome.Added(candidates.map { it.version })
    }

    /** Purely additive: a version already present never reaches this method (R10). */
    private suspend fun applyNew(catalogues: List<Catalogue>) = database.withTransaction {
        val dao = database.catalogueDao()
        val now = time.now().toEpochMilli()
        for (catalogue in catalogues) {
            dao.insertSections(catalogue.sections.map { SectionEntity(it.id, it.label, it.order) })
            dao.insertTaskDefinitions(
                catalogue.tasks.map { TaskDefinitionEntity(it.slug, it.sectionId, it.displayPosition, it.label) },
            )
            dao.insertVersions(
                catalogue.versions.map { CatalogueVersionEntity(it.version, it.effectiveFrom.toString()) },
            )
            dao.insertTaskVersions(
                catalogue.taskVersions.map { it.toEntity(UUID.randomUUID().toString(), now) },
            )
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
