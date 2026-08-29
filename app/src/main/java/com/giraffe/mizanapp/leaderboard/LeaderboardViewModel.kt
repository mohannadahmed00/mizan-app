package com.giraffe.mizanapp.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.OwnRankState
import com.giraffe.mizanapp.domain.leaderboard.Participation
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.usecase.GetParticipationState
import com.giraffe.mizanapp.domain.usecase.GetRanking
import com.giraffe.mizanapp.domain.usecase.SetParticipation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Owns one immutable state for the leaderboard section embedded in Progress. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModel(
    accounts: AccountRepository,
    getParticipationState: GetParticipationState,
    private val setParticipation: SetParticipation,
    private val getRanking: GetRanking,
    sync: SyncRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(emptyState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                accounts.observeSession(),
                getParticipationState(),
                sync.observePendingCount(),
            ) { session, participation, pending -> Context(session, participation, pending) }
                .flatMapLatest { context ->
                    if (context.session is AccountSession.SignedIn && context.participation.optedIn) {
                        getRanking(PeriodKind.WEEKLY).map { ranking -> context.toState(ranking) }
                    } else {
                        flowOf(context.toState(RankingState.Unavailable))
                    }
                }
                .collect { _state.value = it }
        }
    }

    fun join() {
        viewModelScope.launch { setParticipation(true) }
    }

    fun leave() {
        viewModelScope.launch { setParticipation(false) }
    }

    private fun Context.toState(ranking: RankingState): LeaderboardUiState {
        val visibility = when {
            session is AccountSession.SignedOut -> Visibility.Hidden
            !participation.optedIn -> Visibility.Invitation
            else -> Visibility.Participating
        }
        return LeaderboardUiState(
            visibility = visibility,
            selectedPeriod = PeriodKind.WEEKLY,
            ranking = ranking.withProvisional(pending > 0),
            ownRank = OwnRankState.Unavailable,
            honorBoard = HonorBoardState.Unavailable,
            regionLabel = participation.region?.displayName,
            isRefreshing = false,
        )
    }

    private fun RankingState.withProvisional(provisional: Boolean): RankingState = when (this) {
        RankingState.Unavailable -> this
        is RankingState.Cached -> copy(ranking = ranking.copy(isProvisional = ranking.isProvisional || provisional))
        is RankingState.Live -> copy(ranking = ranking.copy(isProvisional = ranking.isProvisional || provisional))
    }

    private data class Context(
        val session: AccountSession,
        val participation: Participation,
        val pending: Int,
    )

    private companion object {
        fun emptyState() = LeaderboardUiState(
            visibility = Visibility.Hidden,
            selectedPeriod = PeriodKind.WEEKLY,
            ranking = RankingState.Unavailable,
            ownRank = OwnRankState.Unavailable,
            honorBoard = HonorBoardState.Unavailable,
            regionLabel = null,
            isRefreshing = false,
        )
    }
}
