package com.giraffe.mizanapp.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serialisation shapes for the remote seam, mapped to and from domain types at
 * this boundary and nowhere else (research R4).
 */
@Serializable
data class RemoteDayRecord(
    @SerialName("user_id") val userId: String,
    val date: String,
    @SerialName("catalogue_version") val catalogueVersion: Int,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * **Carries no `day_plan_id`.** The local plan id is a device-local UUID and
 * means nothing on another device; ingest binds the completion to whatever
 * plan the credited date has locally (research R4).
 */
@Serializable
data class RemoteCompletion(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("credited_date") val creditedDate: String,
    @SerialName("task_slug") val taskSlug: String,
    @SerialName("points_awarded") val pointsAwarded: Int,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("reversed_at") val reversedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RemoteProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class RemotePublication(
    val version: Int,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("format_version") val formatVersion: Int,
    val payload: String,
)
