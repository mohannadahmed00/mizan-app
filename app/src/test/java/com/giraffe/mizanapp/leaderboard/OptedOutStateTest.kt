package com.giraffe.mizanapp.leaderboard

import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.leaderboard.LeaderboardPeriod
import com.giraffe.mizanapp.domain.leaderboard.LoadMoreResult
import com.giraffe.mizanapp.domain.leaderboard.OwnRankState
import com.giraffe.mizanapp.domain.leaderboard.Participation
import com.giraffe.mizanapp.domain.leaderboard.ParticipationResult
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.Ranking
import com.giraffe.mizanapp.domain.leaderboard.RankingEntry
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import com.giraffe.mizanapp.domain.leaderboard.Region
import com.giraffe.mizanapp.domain.leaderboard.RegionId
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import com.giraffe.mizanapp.domain.repository.LeaderboardRepository
import com.giraffe.mizanapp.domain.repository.LocalRecordCounts
import com.giraffe.mizanapp.domain.repository.ParticipationRepository
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.SyncStatus
import com.giraffe.mizanapp.domain.usecase.GetParticipationState
import com.giraffe.mizanapp.domain.usecase.GetRanking
import com.giraffe.mizanapp.domain.usecase.SetParticipation
import com.giraffe.mizanapp.today.FakeClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** After leaving, the section returns exactly to Invitation — no stale ranking, no other name. */
@OptIn(ExperimentalCoroutinesApi::class)
class OptedOutStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val region = Region(RegionId("egypt-cairo"), "Egypt (Cairo)", ZoneId.of("Africa/Cairo"))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun leaving_clears_visibility_ranking_and_every_other_name() = runTest {
        val accounts = FakeAccounts(AccountSession.SignedIn("viewer", "viewer@example.test"))
        val participation = FakeParticipation(Participation(true, region))
        val leaderboard = FakeLeaderboard(ranking())
        val sync = FakeSync()
        val viewModel = LeaderboardViewModel(
            accounts = accounts,
            getParticipationState = GetParticipationState(participation),
            setParticipation = SetParticipation(participation, FakeClock()),
            getRanking = GetRanking(leaderboard),
            sync = sync,
        )
        advanceUntilIdle()
        assertEquals(Visibility.Participating, viewModel.state.value.visibility)

        viewModel.leave()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(Visibility.Invitation, state.visibility)
        assertEquals(RankingState.Unavailable, state.ranking)
        assertFalse(rendersOtherName(state))
    }

    private fun rendersOtherName(state: LeaderboardUiState): Boolean {
        val ranking = (state.ranking as? RankingState.Live)?.ranking ?: return false
        return ranking.entries.any { !it.isViewer }
    }

    private fun ranking() = Ranking(
        period = LeaderboardPeriod(PeriodKind.WEEKLY, LocalDate.parse("2026-03-14"), LocalDate.parse("2026-03-20"), region.id),
        region = region,
        entries = listOf(
            RankingEntry("viewer", "Person", 15, 1, true),
            RankingEntry("other", "Someone else", 10, 2, false),
        ),
        hasMore = false,
        retrievedAt = Instant.parse("2026-03-14T09:00:00Z"),
        isProvisional = false,
    )

    private class FakeParticipation(initial: Participation) : ParticipationRepository {
        val state = MutableStateFlow(initial)
        override fun observe(): Flow<Participation> = state
        override suspend fun optIn(reportedZone: ZoneId) = ParticipationResult.Applied
        override suspend fun optOut(): ParticipationResult {
            state.value = Participation(false, null)
            return ParticipationResult.Applied
        }
        override suspend fun reportZone(zone: ZoneId) = ParticipationResult.Applied
    }

    private class FakeLeaderboard(ranking: Ranking) : LeaderboardRepository {
        private val state = MutableStateFlow<RankingState>(RankingState.Live(ranking))
        override fun observeRanking(kind: PeriodKind): Flow<RankingState> = state
        override fun observeOwnRank(kind: PeriodKind): Flow<OwnRankState> = MutableStateFlow(OwnRankState.Unavailable)
        override suspend fun loadMore(kind: PeriodKind) = LoadMoreResult.Applied
        override suspend fun refresh(kind: PeriodKind) = Unit
    }

    private class FakeSync : SyncRepository {
        val pending = MutableStateFlow(0)
        override fun observeStatus(): Flow<SyncStatus> = MutableStateFlow(SyncStatus.UpToDate)
        override fun observePendingCount(): Flow<Int> = pending
        override fun syncNow() = Unit
    }

    private class FakeAccounts(initial: AccountSession) : AccountRepository {
        val session = MutableStateFlow(initial)
        override fun observeSession(): Flow<AccountSession> = session
        override suspend fun requestCode(email: String): CodeRequest = error("not used")
        override suspend fun confirmCode(email: String, code: String, replaceLocalRecords: Boolean): CodeConfirmation = error("not used")
        override suspend fun signOut(mode: SignOutMode) = Unit
        override suspend fun updateDisplayName(name: String?) = Unit
        override suspend fun localRecordCounts() = LocalRecordCounts(0, 0)
    }
}
