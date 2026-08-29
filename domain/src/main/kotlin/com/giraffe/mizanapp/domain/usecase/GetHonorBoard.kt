package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.repository.HonorBoardRepository
import kotlinx.coroutines.flow.Flow

class GetHonorBoard(private val repository: HonorBoardRepository) {
    operator fun invoke(kind: PeriodKind): Flow<HonorBoardState> = repository.observe(kind)
}
