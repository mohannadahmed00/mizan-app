package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect
import java.time.LocalDate

/**
 * Access to the administrator-defined task catalogue.
 *
 * Declared here, implemented in `:data`. This is the seam roadmap Phase 7 swaps
 * when the catalogue moves server-side: the source changes, the shape does not
 * (Principle VI).
 */
interface CatalogueRepository {

    /**
     * Loads the catalogue into storage if it is not already there.
     *
     * Idempotent (FR-001): calling it twice changes nothing — no duplicate
     * rows, no altered plans, no altered completions. Validates before writing,
     * and a defective catalogue writes nothing at all.
     */
    suspend fun seedIfNeeded(): SeedOutcome

    suspend fun currentVersion(): Int?

    /**
     * The version with the greatest effective-from date on or before [date],
     * or null when the date precedes every version. Never guesses.
     */
    suspend fun versionEffectiveOn(date: LocalDate): Int?

    suspend fun catalogueAt(version: Int): Catalogue?
}

sealed interface SeedOutcome {
    data class Seeded(val version: Int, val taskCount: Int) : SeedOutcome
    data class AlreadyPresent(val version: Int) : SeedOutcome

    /** A first-class outcome the UI must surface — never an empty day (FR-003). */
    data class Failed(val defects: List<CatalogueDefect>) : SeedOutcome
}
