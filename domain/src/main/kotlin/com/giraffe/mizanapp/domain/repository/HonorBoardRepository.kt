package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.flow.Flow

interface HonorBoardRepository {

    /**
     * Qualifying members for a period, and whether the viewer is among them.
     *
     * MUST NOT expose a non-qualifier count, a threshold, a distance to it, or
     * any per-person days figure (FR-030, SC-012). The return type has no field
     * for any of it, so no view can render one by accident (research R8).
     */
    fun observe(kind: PeriodKind): Flow<HonorBoardState>

    suspend fun refresh(kind: PeriodKind)

    // WEEKLY and MONTHLY only (FR-027a). Passing DAILY is a programming error,
    // not a runtime state — the daily period has a ranking and no Honor Board.
}
