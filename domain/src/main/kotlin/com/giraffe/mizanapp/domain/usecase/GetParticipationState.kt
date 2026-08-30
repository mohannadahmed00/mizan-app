package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.leaderboard.Participation
import com.giraffe.mizanapp.domain.repository.ParticipationRepository
import kotlinx.coroutines.flow.Flow

class GetParticipationState(private val repository: ParticipationRepository) {
    operator fun invoke(): Flow<Participation> = repository.observe()
}
