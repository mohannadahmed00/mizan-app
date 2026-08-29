package com.giraffe.mizanapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A disposable snapshot keeps remote standings observable through Room while
 * preserving the app's local-source-of-truth boundary.
 */
@Entity(tableName = "leaderboard_cache")
data class LeaderboardCacheEntity(
    @PrimaryKey val id: String,
    val periodKind: String,
    val periodStart: String,
    val regionId: String,
    val regionDisplayName: String,
    val payload: String,
    val retrievedAt: Long,
)
