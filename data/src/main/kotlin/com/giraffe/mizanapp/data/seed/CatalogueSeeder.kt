package com.giraffe.mizanapp.data.seed

import androidx.room.withTransaction
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.SectionEntity
import com.giraffe.mizanapp.data.db.entities.TaskDefinitionEntity
import com.giraffe.mizanapp.data.mapper.toEntity
import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect
import com.giraffe.mizanapp.domain.catalogue.validateCatalogueContent
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.util.UUID

/**
 * Loads the versioned catalogue into storage, once.
 *
 * Reads the seed from the classpath — `:domain` declares the resource but does
 * no I/O of its own, so reading belongs here.
 *
 * Validates with the `001` contract **before** writing anything, and writes in
 * a single transaction, so a defective catalogue leaves storage untouched
 * rather than half-populated.
 */
class CatalogueSeeder(
    private val database: MizanDatabase,
    private val time: TimeProvider,
    private val resourcePath: String = SEED_RESOURCE,
) {

    suspend fun seedIfNeeded(): SeedOutcome {
        val dao = database.catalogueDao()

        dao.currentVersion()?.let { existing ->
            return SeedOutcome.AlreadyPresent(existing)
        }

        val raw = readResource(resourcePath)
        val defects = validateCatalogueContent(raw = raw, source = resourcePath)
        if (defects.isNotEmpty()) return SeedOutcome.Failed(defects)

        val catalogue = requireNotNull(parsed(raw)) {
            "validation passed but parsing did not — these must agree"
        }

        write(catalogue)

        return SeedOutcome.Seeded(
            version = catalogue.versions.maxOf { it.version },
            taskCount = catalogue.tasks.size,
        )
    }

    private fun parsed(raw: String?): Catalogue? =
        raw?.let { com.giraffe.mizanapp.domain.catalogue.parseCatalogue(it).getOrNull() }

    /** One transaction: a partial catalogue is worse than none at all. */
    private suspend fun write(catalogue: Catalogue) = database.withTransaction {
        val now = time.now().toEpochMilli()
        val dao = database.catalogueDao()

        dao.insertSections(
            catalogue.sections.map { SectionEntity(it.id, it.label, it.order) },
        )
        dao.insertTaskDefinitions(
            catalogue.tasks.map {
                TaskDefinitionEntity(it.slug, it.sectionId, it.displayPosition, it.label)
            },
        )
        dao.insertVersions(
            catalogue.versions.map {
                CatalogueVersionEntity(it.version, it.effectiveFrom.toString())
            },
        )
        dao.insertTaskVersions(
            catalogue.taskVersions.map { it.toEntity(UUID.randomUUID().toString(), now) },
        )
    }

    private fun readResource(path: String): String? =
        CatalogueSeeder::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }

    companion object {
        const val SEED_RESOURCE = "/catalogue/valid-catalogue.json"
    }
}
