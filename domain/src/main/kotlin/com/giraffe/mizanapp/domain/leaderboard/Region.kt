package com.giraffe.mizanapp.domain.leaderboard

import java.time.ZoneId

/** Keeps administrator-assigned region identifiers opaque to domain callers. */
@JvmInline
value class RegionId(val value: String)

/** Couples a regional label with the calendar zone that fixes its periods. */
data class Region(
    val id: RegionId,
    val displayName: String,
    val zone: ZoneId,
)
