package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.leaderboard.ParticipationResult
import com.giraffe.mizanapp.domain.repository.ParticipationRepository
import com.giraffe.mizanapp.domain.time.TimeProvider

/** Keeps device-zone reads behind the canonical clock boundary. */
class SetParticipation(
    private val repository: ParticipationRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(enabled: Boolean): ParticipationResult =
        if (enabled) repository.optIn(time.zone()) else repository.optOut()
}
