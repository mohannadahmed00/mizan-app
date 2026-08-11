package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which catalogue version applies to a date (research.md R1, FR-013b).
 *
 * The earliest loaded version applies open-ended backwards — a catalogue
 * applies until superseded, and the earliest one has nothing before it to
 * defer to. Null is reserved for a genuine absence: no catalogue loaded at
 * all.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueVersionResolutionTest : DbTestBase() {

    @Test
    fun a_date_on_or_after_the_seeds_effective_from_resolves_to_that_version() = runTest {
        catalogue.seedIfNeeded()

        assertEquals(1, catalogue.versionEffectiveOn(LocalDate.parse("2026-08-08")))
    }

    @Test
    fun a_date_before_the_earliest_version_still_resolves_to_it() = runTest {
        catalogue.seedIfNeeded()

        // The shipped seed declares version 1 effective from 2026-01-01.
        assertEquals(1, catalogue.versionEffectiveOn(LocalDate.parse("2025-06-01")))
    }

    @Test
    fun no_catalogue_loaded_at_all_resolves_to_null() = runTest {
        assertNull(catalogue.versionEffectiveOn(LocalDate.parse("2025-06-01")))
        assertNull(catalogue.versionEffectiveOn(LocalDate.parse("2026-08-08")))
    }
}
