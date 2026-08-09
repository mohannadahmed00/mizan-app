package com.giraffe.mizanapp.domain.catalogue

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import java.time.DayOfWeek

/**
 * Decides whether a task catalogue is admissible.
 *
 * Pure: no clock, no I/O, no Android, no logging. Never throws — every defect
 * is a returned value. Never stops at the first defect, so authoring 40 records
 * does not take 40 runs. An empty list is the only success signal.
 */
class CatalogueValidator {

    fun validate(catalogue: Catalogue): List<CatalogueDefect> {
        val defects = buildList {
            addAll(identityDefects(catalogue))
            addAll(shapeDefects(catalogue))
            addAll(placementDefects(catalogue))
            addAll(versioningDefects(catalogue))
            addAll(arithmeticDefects(catalogue))
        }
        // Deterministic order so assertions are stable across runs.
        return defects.sortedBy { it.toString() }
    }

    // --- rules 1, 2 : identity ----------------------------------------------

    private fun identityDefects(catalogue: Catalogue): List<CatalogueDefect> = buildList {
        catalogue.tasks
            .groupBy { it.slug }
            .filter { (_, group) -> group.size > 1 }
            .forEach { (slug, group) ->
                add(CatalogueDefect.DuplicateTaskSlug(slug, group.size))
            }

        catalogue.tasks
            .map { it.slug }
            .distinct()
            .filterNot { SLUG_PATTERN.matches(it) }
            .forEach { add(CatalogueDefect.MalformedSlug(it)) }
    }

    // --- rules 3, 4, 8 : per-task shape -------------------------------------

    private fun shapeDefects(catalogue: Catalogue): List<CatalogueDefect> = buildList {
        catalogue.taskVersions.forEach { version ->
            if (version.points <= 0) {
                add(CatalogueDefect.NonPositivePoints(version.taskSlug, version.points))
            }
            if (version.maxOccurrencesPerDay < 1) {
                add(
                    CatalogueDefect.InvalidOccurrenceLimit(
                        version.taskSlug,
                        version.maxOccurrencesPerDay,
                    )
                )
            }
            if (version.scheduleRule.matchingDays().isEmpty()) {
                add(CatalogueDefect.UnreachableSchedule(version.taskSlug))
            }
        }
    }

    // --- rules 5, 6, 7 : placement ------------------------------------------

    private fun placementDefects(catalogue: Catalogue): List<CatalogueDefect> = buildList {
        val sectionIds = catalogue.sections.map { it.id }.toSet()

        catalogue.tasks
            .filterNot { it.sectionId in sectionIds }
            .forEach { add(CatalogueDefect.UnknownSection(it.slug, it.sectionId)) }

        // Display position is scoped to the section: reuse across sections is legal.
        catalogue.tasks
            .groupBy { it.sectionId }
            .forEach { (sectionId, tasksInSection) ->
                tasksInSection
                    .groupBy { it.displayPosition }
                    .filter { (_, group) -> group.size > 1 }
                    .forEach { (position, group) ->
                        add(
                            CatalogueDefect.DuplicateDisplayPosition(
                                sectionId = sectionId,
                                position = position,
                                slugs = group.map { it.slug },
                            )
                        )
                    }
            }

        catalogue.sections
            .groupBy { it.order }
            .filter { (_, group) -> group.size > 1 }
            .forEach { (order, group) ->
                add(CatalogueDefect.DuplicateSectionOrder(order, group.map { it.id }))
            }
    }

    // --- rules 9, 10, 11 : versioning ---------------------------------------

    private fun versioningDefects(catalogue: Catalogue): List<CatalogueDefect> = buildList {
        catalogue.versions
            .groupBy { it.version }
            .filter { (_, group) -> group.size > 1 }
            .forEach { (version, group) ->
                add(CatalogueDefect.DuplicateVersionNumber(version, group.size))
            }

        catalogue.versions
            .groupBy { it.effectiveFrom }
            .filter { (_, group) -> group.size > 1 }
            .forEach { (date, group) ->
                add(
                    CatalogueDefect.DuplicateEffectiveFrom(
                        date = date.toString(),
                        versions = group.map { it.version }.sorted(),
                    )
                )
            }

        // Version order and effective-from order must agree, so that "which
        // version applied on this date" has exactly one answer.
        val byVersion = catalogue.versions.sortedBy { it.version }
        byVersion.zipWithNext().forEach { (earlier, later) ->
            if (later.version != earlier.version && !later.effectiveFrom.isAfter(earlier.effectiveFrom)) {
                add(
                    CatalogueDefect.VersionOrderMismatch(
                        version = later.version,
                        effectiveFrom = later.effectiveFrom.toString(),
                    )
                )
            }
        }
    }

    // --- rules 12, 13, 14 : arithmetic --------------------------------------

    private fun arithmeticDefects(catalogue: Catalogue): List<CatalogueDefect> = buildList {
        DayOfWeek.entries.forEach { day ->
            val expected = EXPECTED_WEEKDAY_TOTALS.getValue(day)
            val actual = catalogue.availablePointsOn(day)
            if (actual != expected) {
                add(CatalogueDefect.WeekdayTotalMismatch(day.name, expected, actual))
            }
        }

        val week = catalogue.weeklyAvailablePoints()
        if (week != EXPECTED_WEEK_TOTAL) {
            add(CatalogueDefect.WeekTotalMismatch(EXPECTED_WEEK_TOTAL, week))
        }

        EXPECTED_SECTION_TOTALS.forEach { (sectionId, expected) ->
            val actual = catalogue.sectionPointsOn(sectionId, BASE_DAY)
            if (actual != expected) {
                add(CatalogueDefect.SectionCompositionMismatch(sectionId, expected, actual))
            }
        }
    }

    companion object {
        private val SLUG_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

        /** A weekday carrying neither the fast nor the Friday activities. */
        private val BASE_DAY = DayOfWeek.SATURDAY

        const val EXPECTED_WEEK_TOTAL = 500

        val EXPECTED_WEEKDAY_TOTALS: Map<DayOfWeek, Int> = mapOf(
            DayOfWeek.SATURDAY to 69,
            DayOfWeek.SUNDAY to 69,
            DayOfWeek.MONDAY to 74,
            DayOfWeek.TUESDAY to 69,
            DayOfWeek.WEDNESDAY to 69,
            DayOfWeek.THURSDAY to 74,
            DayOfWeek.FRIDAY to 76,
        )

        /** Section totals on a base day. Fasting and Friday do not apply then. */
        val EXPECTED_SECTION_TOTALS: Map<String, Int> = mapOf(
            "fajr" to 12,
            "dhuhr" to 8,
            "asr" to 6,
            "maghrib" to 6,
            "isha" to 6,
            "qiyam-witr" to 9,
            "quran" to 4,
            "adhkar" to 18,
        )
    }
}
