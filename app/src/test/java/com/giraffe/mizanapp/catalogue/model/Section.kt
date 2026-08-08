package com.giraffe.mizanapp.catalogue.model

import kotlinx.serialization.Serializable

/**
 * The grouping a task is displayed and totalled under.
 *
 * Sections carry their own ordering; task display positions are scoped inside
 * a section, so two tasks in different sections may share a position.
 */
@Serializable
data class Section(
    val id: String,
    val label: String,
    val order: Int,
)
