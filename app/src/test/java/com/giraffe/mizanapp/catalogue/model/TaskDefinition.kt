package com.giraffe.mizanapp.catalogue.model

import kotlinx.serialization.Serializable

/**
 * A practice that can be recorded. Identity and placement only.
 *
 * The [slug] is stable across point and schedule changes, never reused, and
 * never rewritten once published.
 */
@Serializable
data class TaskDefinition(
    val slug: String,
    val sectionId: String,
    val displayPosition: Int,
    val label: String,
)
