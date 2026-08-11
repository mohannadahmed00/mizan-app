package com.giraffe.mizanapp.domain.week

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.day.resolveApplicableTasks
import java.time.LocalDate

/**
 * What a date's available points *would* be, computed against a catalogue
 * version without creating or persisting anything (FR-009d).
 *
 * Reuses `002`'s applicability rule so a projected day and a materialised day
 * can never disagree about which tasks apply — there is one applicability
 * rule, not two.
 */
fun projectAvailablePoints(catalogue: Catalogue, version: Int, date: LocalDate): Int =
    resolveApplicableTasks(catalogue, version, date).sumOf { it.points * it.maxOccurrencesPerDay }
