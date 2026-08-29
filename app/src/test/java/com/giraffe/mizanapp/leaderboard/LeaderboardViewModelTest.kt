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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val region = Region(RegionId("egypt-cairo"), "Egypt (Cairo)", ZoneId.of("Africa/Cairo"))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun visibility_follows_session_and_explicit_participation() = runTest {
        val fixture = fixture(AccountSession.SignedOut, Participation(false, null))
        advanceUntilIdle()
        assertEquals(Visibility.Hidden, fixture.viewModel.state.value.visibility)

        fixture.accounts.session.value = AccountSession.SignedIn("viewer", "viewer@example.test")
        advanceUntilIdle()
        assertEquals(Visibility.Invitation, fixture.viewModel.state.value.visibility)

        fixture.participation.state.value = Participation(true, region)
        advanceUntilIdle()
        assertEquals(Visibility.Participating, fixture.viewModel.state.value.visibility)
        assertEquals(region.displayName, fixture.viewModel.state.value.regionLabel)
    }

    @Test
    fun pending_completions_make_the_server_ranking_provisional() = runTest {
        val fixture = fixture(AccountSession.SignedIn("viewer", "viewer@example.test"), Participation(true, region))
        fixture.sync.pending.value = 2
        advanceUntilIdle()

        val state = fixture.viewModel.state.value.ranking as RankingState.Live
        assertTrue(state.ranking.isProvisional)
    }

    @Test
    fun state_copy_never_uses_blame_or_comparison_words() = runTest {
        val fixture = fixture(AccountSession.SignedIn("viewer", "viewer@example.test"), Participation(true, region))
        advanceUntilIdle()
        val state = fixture.viewModel.state.value
        val strings = buildList {
            state.regionLabel?.let(::add)
            val ranking = (state.ranking as RankingState.Live).ranking
            add(ranking.region.displayName)
            addAll(ranking.entries.map { it.displayName })
        }
        val forbidden = listOf(
            "failed", "failure", "error", "lost", "missing", "problem", "wrong",
            "you didn't", "you haven't", "retry now", "behind", "climb", "overtake",
        )
        strings.forEach { text -> forbidden.forEach { assertFalse(text.contains(it, ignoreCase = true)) } }
    }

    private fun fixture(session: AccountSession, initialParticipation: Participation): Fixture {
        val accounts = FakeAccounts(session)
        val participation = FakeParticipation(initialParticipation)
        val leaderboard = FakeLeaderboard(ranking())
        val sync = FakeSync()
        return Fixture(
            LeaderboardViewModel(
                accounts = accounts,
                getParticipationState = GetParticipationState(participation),
                setParticipation = SetParticipation(participation, FakeClock()),
                getRanking = GetRanking(leaderboard),
                sync = sync,
            ),
            accounts,
            participation,
            sync,
        )
    }

    private fun ranking() = Ranking(
        period = LeaderboardPeriod(PeriodKind.WEEKLY, LocalDate.parse("2026-03-14"), LocalDate.parse("2026-03-20"), region.id),
        region = region,
        entries = listOf(RankingEntry("viewer", "Person", 15, 1, true)),
        hasMore = false,
        retrievedAt = Instant.parse("2026-03-14T09:00:00Z"),
        isProvisional = false,
    )

    private data class Fixture(
        val viewModel: LeaderboardViewModel,
        val accounts: FakeAccounts,
        val participation: FakeParticipation,
        val sync: FakeSync,
    )

    private class FakeParticipation(initial: Participation) : ParticipationRepository {
        val state = MutableStateFlow(initial)
        override fun observe(): Flow<Participation> = state
        override suspend fun optIn(reportedZone: ZoneId) = ParticipationResult.Applied
        override suspend fun optOut() = ParticipationResult.Applied
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
