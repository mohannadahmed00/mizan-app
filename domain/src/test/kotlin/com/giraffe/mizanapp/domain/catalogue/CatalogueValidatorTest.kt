package com.giraffe.mizanapp.domain.catalogue

import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.DuplicateDisplayPosition
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.DuplicateEffectiveFrom
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.DuplicateSectionOrder
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.DuplicateTaskSlug
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.DuplicateVersionNumber
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.InvalidOccurrenceLimit
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.MalformedCatalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.MalformedSlug
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.NoCatalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.NonPositivePoints
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.SectionCompositionMismatch
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.UnknownSection
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.UnreachableSchedule
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.UserAuthoringAffordance
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.VersionOrderMismatch
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.WeekTotalMismatch
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.WeekdayTotalMismatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueValidatorTest {

    private val validator = CatalogueValidator()

    private fun defectsInBad(name: String): List<CatalogueDefect> =
        loadAndValidate("/catalogue/bad/$name", validator)

    private inline fun <reified T : CatalogueDefect> List<CatalogueDefect>.only(): T {
        val hits = filterIsInstance<T>()
        assertEquals("expected exactly one ${T::class.simpleName} in $this", 1, hits.size)
        return hits.single()
    }

    private inline fun <reified T : CatalogueDefect> List<CatalogueDefect>.has(): Boolean =
        any { it is T }

    // --- positive control ---------------------------------------------------

    @Test
    fun `valid catalogue has no defects`() {
        assertEquals(emptyList<CatalogueDefect>(), loadAndValidate(Fixtures.GOOD, validator))
    }

    // --- rule 1..8 : identity and shape -------------------------------------

    @Test
    fun `rule 1 - duplicate task slug is rejected`() {
        val defect = defectsInBad("duplicate-slug.json").only<DuplicateTaskSlug>()
        assertEquals("fajr-1", defect.slug)
        assertEquals(2, defect.count)
    }

    @Test
    fun `rule 2 - malformed slug is rejected`() {
        assertEquals("Asr_1", defectsInBad("malformed-slug.json").only<MalformedSlug>().slug)
    }

    @Test
    fun `rule 3a - zero points is rejected`() {
        val defect = defectsInBad("zero-points.json").only<NonPositivePoints>()
        assertEquals("fajr-1", defect.slug)
        assertEquals(0, defect.points)
    }

    @Test
    fun `rule 3b - negative points is rejected`() {
        val defect = defectsInBad("negative-points.json").only<NonPositivePoints>()
        assertEquals("fajr-1", defect.slug)
        assertEquals(-2, defect.points)
    }

    @Test
    fun `rule 4 - zero occurrence limit is rejected`() {
        val defect = defectsInBad("zero-occurrences.json").only<InvalidOccurrenceLimit>()
        assertEquals("witr", defect.slug)
        assertEquals(0, defect.value)
    }

    @Test
    fun `rule 5 - unknown section is rejected`() {
        val defect = defectsInBad("missing-section.json").only<UnknownSection>()
        assertEquals("isha-1", defect.slug)
        assertEquals("nonexistent", defect.sectionId)
    }

    @Test
    fun `rule 6 - duplicate display position within a section is rejected`() {
        val defect = defectsInBad("duplicate-position-in-section.json")
            .only<DuplicateDisplayPosition>()
        assertEquals("fajr", defect.sectionId)
        assertEquals(1, defect.position)
        assertEquals(listOf("fajr-1", "fajr-2"), defect.slugs)
    }

    @Test
    fun `rule 6 negative control - different sections may share a display position`() {
        // The good fixture has position 1 in all ten sections and must stay clean.
        assertTrue(
            "cross-section position reuse must be legal",
            !loadAndValidate(Fixtures.GOOD, validator).has<DuplicateDisplayPosition>(),
        )
    }

    @Test
    fun `rule 7 - duplicate section order is rejected`() {
        val defect = defectsInBad("duplicate-section-order.json").only<DuplicateSectionOrder>()
        assertEquals(1, defect.order)
        assertEquals(listOf("fajr", "dhuhr"), defect.sectionIds)
    }

    @Test
    fun `rule 8 - schedule matching no weekday is rejected`() {
        assertEquals(
            "friday-1",
            defectsInBad("unreachable-schedule.json").only<UnreachableSchedule>().slug,
        )
    }

    // --- rule 9..11 : versioning --------------------------------------------

    @Test
    fun `rule 9 - duplicate version number is rejected`() {
        val defect = defectsInBad("duplicate-version-number.json").only<DuplicateVersionNumber>()
        assertEquals(1, defect.version)
        assertEquals(2, defect.count)
    }

    @Test
    fun `rule 10 - version order disagreeing with date order is rejected`() {
        val defect = defectsInBad("version-order-mismatch.json").only<VersionOrderMismatch>()
        assertEquals(2, defect.version)
        assertEquals("2025-01-01", defect.effectiveFrom)
    }

    @Test
    fun `rule 11 - duplicate effective-from is rejected`() {
        val defect = defectsInBad("duplicate-effective-from.json").only<DuplicateEffectiveFrom>()
        assertEquals("2026-01-01", defect.date)
        assertEquals(listOf(1, 2), defect.versions)
    }

    // --- rule 12..14 : arithmetic -------------------------------------------

    @Test
    fun `rule 12 - wrong weekday total is rejected`() {
        val defects = defectsInBad("wrong-weekday-total.json")
        assertTrue("expected a weekday total defect in $defects", defects.has<WeekdayTotalMismatch>())

        val saturday = defects.filterIsInstance<WeekdayTotalMismatch>()
            .single { it.dayOfWeek == "SATURDAY" }
        assertEquals(69, saturday.expected)
        assertEquals(70, saturday.actual)
    }

    @Test
    fun `rule 13 - wrong week total is rejected`() {
        val defect = defectsInBad("wrong-week-total.json").only<WeekTotalMismatch>()
        assertEquals(500, defect.expected)
        assertEquals(501, defect.actual)
    }

    @Test
    fun `rule 14 - wrong section composition is rejected`() {
        val defect = defectsInBad("wrong-section-composition.json")
            .filterIsInstance<SectionCompositionMismatch>()
            .single { it.sectionId == "adhkar" }
        assertEquals(18, defect.expected)
        assertEquals(16, defect.actual)
    }

    // --- rule 15..17 : boundary ---------------------------------------------

    @Test
    fun `rule 15 - user authoring affordance is named, not swallowed as a parse error`() {
        val defects = defectsInBad("user-editable-flag.json")
        assertEquals("editable", defects.only<UserAuthoringAffordance>().field)
        assertTrue(
            "FR-019 must not degrade into a generic parse error",
            !defects.has<MalformedCatalogue>(),
        )
    }

    @Test
    fun `rule 16 - unparseable catalogue is rejected`() {
        val defects = defectsInBad("malformed.json")
        assertTrue("expected MalformedCatalogue in $defects", defects.has<MalformedCatalogue>())
        assertTrue(
            "a plain typo must not be reported as an authoring affordance",
            !defects.has<UserAuthoringAffordance>(),
        )
    }

    @Test
    fun `rule 17 - absent catalogue is not the same as a valid one`() {
        val defects = loadAndValidate(Fixtures.MISSING, validator)
        assertEquals(Fixtures.MISSING, defects.only<NoCatalogue>().path)
        assertTrue("absence must never read as success", defects.isNotEmpty())
    }

    // --- cross-cutting guarantees -------------------------------------------

    @Test
    fun `validator never throws on any fixture`() {
        Fixtures.badFixtureNames().forEach { name ->
            val defects = loadAndValidate("/catalogue/bad/$name", validator)
            assertTrue("$name produced no defect at all", defects.isNotEmpty())
        }
    }

    @Test
    fun `validator reports all defects, not just the first`() {
        val defects = defectsInBad("two-defects.json")
        assertTrue("expected points defect in $defects", defects.has<NonPositivePoints>())
        assertTrue("expected section order defect in $defects", defects.has<DuplicateSectionOrder>())
    }

    @Test
    fun `defect order is stable across runs`() {
        val first = defectsInBad("two-defects.json")
        val second = defectsInBad("two-defects.json")
        assertEquals(first, second)
    }
}
