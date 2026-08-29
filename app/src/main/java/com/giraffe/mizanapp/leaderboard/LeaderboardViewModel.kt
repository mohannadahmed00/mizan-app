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
import com.giraffe.mizanapp.domain.usecase.GetHonorBoard
import com.giraffe.mizanapp.domain.usecase.GetOwnRank
import com.giraffe.mizanapp.domain.usecase.GetParticipationState
import com.giraffe.mizanapp.domain.usecase.GetRanking
import com.giraffe.mizanapp.domain.usecase.ReconcileZone
import com.giraffe.mizanapp.domain.usecase.SetParticipation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Owns one immutable state for the leaderboard section embedded in Progress. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModel(
    accounts: AccountRepository,
    getParticipationState: GetParticipationState,
    private val setParticipation: SetParticipation,
    private val getRanking: GetRanking,
    private val getOwnRank: GetOwnRank,
    private val getHonorBoard: GetHonorBoard,
    private val reconcileZone: ReconcileZone,
    sync: SyncRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(emptyState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()
    private val selectedPeriod = MutableStateFlow(PeriodKind.WEEKLY)

    init {
        // FR-013: checked here (section opened) and covers app start, since this
        // is the section's first composition — no background worker or receiver.
        viewModelScope.launch { reconcileZone() }
        viewModelScope.launch {
            combine(
                accounts.observeSession(),
                getParticipationState(),
                sync.observePendingCount(),
                selectedPeriod,
            ) { session, participation, pending, period -> Context(session, participation, pending, period) }
                .flatMapLatest { context ->
                    if (context.session is AccountSession.SignedIn && context.participation.optedIn) {
                        val honorBoard = if (context.period == PeriodKind.DAILY) {
                            flowOf(HonorBoardState.Unavailable)
                        } else {
                            getHonorBoard(context.period)
                        }
                        combine(getRanking(context.period), getOwnRank(context.period), honorBoard) { ranking, ownRank, board ->
                            context.toState(ranking, ownRank, board)
                        }
                    } else {
                        flowOf(context.toState(RankingState.Unavailable, OwnRankState.Unavailable, HonorBoardState.Unavailable))
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

    fun selectPeriod(kind: PeriodKind) {
        selectedPeriod.value = kind
    }

    private fun Context.toState(ranking: RankingState, ownRank: OwnRankState, honorBoard: HonorBoardState): LeaderboardUiState {
        val visibility = when {
            session is AccountSession.SignedOut -> Visibility.Hidden
            !participation.optedIn -> Visibility.Invitation
            else -> Visibility.Participating
        }
        return LeaderboardUiState(
            visibility = visibility,
            selectedPeriod = period,
            ranking = ranking.withProvisional(pending > 0),
            ownRank = ownRank,
            honorBoard = honorBoard,
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
        val period: PeriodKind,
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
