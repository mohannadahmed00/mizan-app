package com.giraffe.mizanapp.domain.catalogue

/**
 * The validator's output vocabulary. One variant per rule, each carrying enough
 * context to fix the file without re-running.
 *
 * 17 variants, 17 rules. An empty list is the only success signal.
 */
sealed interface CatalogueDefect {

    // --- identity and shape -------------------------------------------------

    data class DuplicateTaskSlug(val slug: String, val count: Int) : CatalogueDefect

    data class MalformedSlug(val slug: String) : CatalogueDefect

    data class NonPositivePoints(val slug: String, val points: Int) : CatalogueDefect

    data class InvalidOccurrenceLimit(val slug: String, val value: Int) : CatalogueDefect

    data class UnknownSection(val slug: String, val sectionId: String) : CatalogueDefect

    data class DuplicateDisplayPosition(
        val sectionId: String,
        val position: Int,
        val slugs: List<String>,
    ) : CatalogueDefect

    data class DuplicateSectionOrder(
        val order: Int,
        val sectionIds: List<String>,
    ) : CatalogueDefect

    data class UnreachableSchedule(val slug: String) : CatalogueDefect

    // --- versioning ---------------------------------------------------------

    data class DuplicateVersionNumber(val version: Int, val count: Int) : CatalogueDefect

    data class VersionOrderMismatch(val version: Int, val effectiveFrom: String) : CatalogueDefect

    data class DuplicateEffectiveFrom(val date: String, val versions: List<Int>) : CatalogueDefect

    // --- arithmetic ---------------------------------------------------------

    data class WeekdayTotalMismatch(
        val dayOfWeek: String,
        val expected: Int,
        val actual: Int,
    ) : CatalogueDefect

    data class WeekTotalMismatch(val expected: Int, val actual: Int) : CatalogueDefect

    data class SectionCompositionMismatch(
        val sectionId: String,
        val expected: Int,
        val actual: Int,
    ) : CatalogueDefect

    // --- boundary -----------------------------------------------------------

    /** A field implying the user may author the catalogue. FR-019, Principle VI. */
    data class UserAuthoringAffordance(val field: String) : CatalogueDefect

    data class MalformedCatalogue(val message: String) : CatalogueDefect

    /** Distinct from an empty defect list: absence is not validity. FR-011. */
    data class NoCatalogue(val path: String) : CatalogueDefect
}
