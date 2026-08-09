package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.catalogue.TaskVersion
import java.time.LocalDate

/**
 * The tasks that apply on a date, under a given catalogue version.
 *
 * Applicability comes from the catalogue's schedule rules alone (FR-005). No
 * task may be shown or hidden by a rule written into a screen.
 *
 * Pure: no clock, no I/O.
 */
fun resolveApplicableTasks(
    catalogue: Catalogue,
    version: Int,
    date: LocalDate,
): List<TaskVersion> =
    catalogue.taskVersions.filter {
        it.catalogueVersion == version && it.scheduleRule.matches(date.dayOfWeek)
    }
