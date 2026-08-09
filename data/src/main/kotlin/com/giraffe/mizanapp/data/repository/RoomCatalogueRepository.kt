package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.mapper.toDomain
import com.giraffe.mizanapp.data.seed.CatalogueSeeder
import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueVersion
import com.giraffe.mizanapp.domain.catalogue.Section
import com.giraffe.mizanapp.domain.catalogue.TaskDefinition
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import java.time.LocalDate

class RoomCatalogueRepository(
    private val database: MizanDatabase,
    private val seeder: CatalogueSeeder,
) : CatalogueRepository {

    override suspend fun seedIfNeeded(): SeedOutcome = seeder.seedIfNeeded()

    override suspend fun currentVersion(): Int? = database.catalogueDao().currentVersion()

    override suspend fun versionEffectiveOn(date: LocalDate): Int? =
        database.catalogueDao().versionEffectiveOn(date.toString())

    override suspend fun catalogueAt(version: Int): Catalogue? {
        val dao = database.catalogueDao()
        val versions = dao.versions()
        if (versions.none { it.version == version }) return null

        return Catalogue(
            versions = versions.map { CatalogueVersion(it.version, LocalDate.parse(it.effectiveFrom)) },
            sections = dao.sections().map { Section(it.id, it.label, it.displayOrder) },
            tasks = dao.taskDefinitions().map {
                TaskDefinition(it.slug, it.sectionId, it.displayPosition, it.label)
            },
            taskVersions = dao.taskVersionsFor(version).map { it.toDomain() },
        )
    }
}
