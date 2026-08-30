package com.giraffe.mizanapp.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A bounded server-ranked page; the client never reorders or recalculates it. */
@Serializable
data class RemoteRankingPage(
    @SerialName("period_kind") val periodKind: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end_inclusive") val periodEndInclusive: String,
    @SerialName("region_id") val regionId: String,
    @SerialName("region_display_name") val regionDisplayName: String,
    @SerialName("region_zone") val regionZone: String,
    val entries: List<RemoteRankingEntry>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("is_final") val isFinal: Boolean,
)

/** Contains only the neutral facts published for one ranking row. */
@Serializable
data class RemoteRankingEntry(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val points: Int,
    val position: Int,
)

/** Returns the viewer and immediate neighbours without scanning ranking pages. */
@Serializable
data class RemoteOwnRank(
    val entry: RemoteRankingEntry? = null,
    val neighbours: List<RemoteRankingEntry>,
    @SerialName("total_participants") val totalParticipants: Int,
)

/** Qualifying members and period context, with no non-qualifier information. */
@Serializable
data class RemoteHonorBoard(
    @SerialName("period_kind") val periodKind: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end_inclusive") val periodEndInclusive: String,
    @SerialName("region_id") val regionId: String,
    @SerialName("region_display_name") val regionDisplayName: String,
    @SerialName("region_zone") val regionZone: String,
    val members: List<RemoteHonorBoardMember>,
    @SerialName("viewer_qualified") val viewerQualified: Boolean,
)

/** Identifies the viewer without exposing points, days, or a position. */
@Serializable
data class RemoteHonorBoardMember(
    @SerialName("display_name") val displayName: String,
    @SerialName("is_viewer") val isViewer: Boolean,
)

/** The service-assigned participation state returned after zone reporting. */
@Serializable
data class RemoteParticipation(
    @SerialName("opted_in") val optedIn: Boolean,
    @SerialName("region_id") val regionId: String? = null,
    @SerialName("region_display_name") val regionDisplayName: String? = null,
    @SerialName("region_zone") val regionZone: String? = null,
)
