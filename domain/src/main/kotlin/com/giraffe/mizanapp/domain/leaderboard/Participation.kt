package com.giraffe.mizanapp.domain.leaderboard

/** Keeps consent off until the service has assigned a region. */
data class Participation(
    val optedIn: Boolean,
    val region: Region?,
)

/** Models neutral outcomes without attaching blame-carrying message text. */
sealed interface ParticipationResult {
    data object Applied : ParticipationResult
    data object Unreachable : ParticipationResult
    data object SessionExpired : ParticipationResult
}
