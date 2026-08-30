package com.giraffe.mizanapp.di

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.accountScopeDaoOf
import com.giraffe.mizanapp.data.db.boundaryStateDaoOf
import com.giraffe.mizanapp.data.db.createMizanDatabase
import com.giraffe.mizanapp.data.prayer.AdhanPrayerTimes
import com.giraffe.mizanapp.data.prayer.AndroidLocationSource
import com.giraffe.mizanapp.data.repository.OutboxSyncRepository
import com.giraffe.mizanapp.data.repository.RemoteCataloguePublicationRepository
import com.giraffe.mizanapp.data.repository.RoomCatalogueRepository
import com.giraffe.mizanapp.data.repository.RoomCompletionRepository
import com.giraffe.mizanapp.data.repository.RoomDayPlanRepository
import com.giraffe.mizanapp.data.repository.RoomHonorBoardRepository
import com.giraffe.mizanapp.data.repository.RoomLeaderboardRepository
import com.giraffe.mizanapp.data.repository.RoomParticipationRepository
import com.giraffe.mizanapp.data.repository.RoomRecordCoverageRepository
import com.giraffe.mizanapp.data.repository.SyncingCompletionRepository
import com.giraffe.mizanapp.data.repository.SyncingDayPlanRepository
import com.giraffe.mizanapp.data.repository.createAccountRepository
import com.giraffe.mizanapp.data.seed.CatalogueSeeder
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Backfill
import com.giraffe.mizanapp.data.sync.LeaderboardRefresh
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.data.sync.SyncScheduler
import com.giraffe.mizanapp.data.sync.SyncWorker
import com.giraffe.mizanapp.data.sync.createRemoteDataSource
import com.giraffe.mizanapp.data.sync.endSupabaseSession
import com.giraffe.mizanapp.data.sync.isSupabaseConfigured
import com.giraffe.mizanapp.domain.repository.CataloguePublicationRepository
import com.giraffe.mizanapp.data.time.BoundaryStateStore
import com.giraffe.mizanapp.data.time.SystemTimeProvider
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.prayer.LocationSource
import com.giraffe.mizanapp.domain.prayer.PrayerTimesProvider
import com.giraffe.mizanapp.domain.prayer.loadRegionConventionMapping
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.HonorBoardRepository
import com.giraffe.mizanapp.domain.repository.LeaderboardRepository
import com.giraffe.mizanapp.domain.repository.ParticipationRepository
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.time.BoundaryStatus
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.usecase.ConfirmSignInCode
import com.giraffe.mizanapp.domain.usecase.GetDayDetail
import com.giraffe.mizanapp.domain.usecase.GetDaySummary
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.GetHonorBoard
import com.giraffe.mizanapp.domain.usecase.GetMonthOverview
import com.giraffe.mizanapp.domain.usecase.GetOwnRank
import com.giraffe.mizanapp.domain.usecase.GetParticipationState
import com.giraffe.mizanapp.domain.usecase.GetPersonalBests
import com.giraffe.mizanapp.domain.usecase.GetRanking
import com.giraffe.mizanapp.domain.usecase.GetSectionBreakdown
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import com.giraffe.mizanapp.domain.usecase.GetWeekSummary
import com.giraffe.mizanapp.domain.usecase.GetWeeklyTrend
import com.giraffe.mizanapp.domain.usecase.ReconcileZone
import com.giraffe.mizanapp.domain.usecase.RequestSignInCode
import com.giraffe.mizanapp.domain.usecase.SetParticipation
import com.giraffe.mizanapp.domain.usecase.SignOut
import com.giraffe.mizanapp.domain.usecase.UpdateDisplayName
import com.giraffe.mizanapp.auth.SignInViewModel
import com.giraffe.mizanapp.daysummary.DaySummaryViewModel
import com.giraffe.mizanapp.history.HistoryViewModel
import com.giraffe.mizanapp.insights.InsightsViewModel
import com.giraffe.mizanapp.leaderboard.LeaderboardViewModel
import com.giraffe.mizanapp.profile.ProfileViewModel
import com.giraffe.mizanapp.sync.SyncStatusViewModel
import com.giraffe.mizanapp.today.TodayViewModel
import com.giraffe.mizanapp.week.WeekViewModel
import java.time.LocalDate
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin is the sole DI framework (constitution, Technology Constraints).
 *
 * This wiring is the one thing in the feature that claims Principle I's
 * test-first exemption — it is configuration, not behaviour.
 */

val domainModule = module {
    single<TimeProvider> { SystemTimeProvider(get()) }
    factory { DayWritePolicy(get()) }
    factory { GetWeekSummary(get(), get(), get(), get(), get()) }
    factory { GetDaySummary(get(), get()) }
    factory { GetStreakSummary(get(), get(), get(), get()) }
    factory { GetHistoryPage(get(), get(), get(), get(), get()) }
    factory { GetDayDetail(get(), get(), get(), get()) }
    factory { GetWeeklyTrend(get()) }
    factory { GetMonthOverview(get(), get(), get(), get(), get()) }
    factory { GetSectionBreakdown(get(), get(), get(), get(), get()) }
    factory { GetPersonalBests(get(), get(), get(), get(), get()) }
    factory { GetParticipationState(get()) }
    factory { SetParticipation(get(), get()) }
    factory { GetRanking(get()) }
    factory { GetOwnRank(get()) }
    factory { GetHonorBoard(get()) }
    factory { ReconcileZone(get(), get()) }
}

val dataModule = module {
    // Construction lives in :data so Room stays behind that boundary.
    single<MizanDatabase> { createMizanDatabase(androidContext()) }

    // spec 009: the Maghrib day boundary. BoundaryStatus binds to the same BoundaryStateStore
    // singleton `today()` reads through -- never a second construction, or the app would
    // observe one state while today() reads another.
    single<LocationSource> { AndroidLocationSource(androidContext()) }
    single<PrayerTimesProvider> { AdhanPrayerTimes(loadRegionConventionMapping()) }
    single { BoundaryStateStore(boundaryStateDaoOf(get()), get(), get()) }
    single<BoundaryStatus> { get<BoundaryStateStore>() }

    single { CatalogueSeeder(get(), get()) }
    single<CatalogueRepository> { RoomCatalogueRepository(get(), get()) }
    single<RecordCoverageRepository> { RoomRecordCoverageRepository(get(), get()) }

    // Room implementations stay registered by concrete type; CompletionRepository and
    // DayPlanRepository resolve to the sync-decorated wrappers so every existing use
    // case keeps working through the same interfaces (spec 007 T082).
    single { RoomDayPlanRepository(get(), get(), get()) }
    single { RoomCompletionRepository(get(), get(), get(), get()) }
    single<DayPlanRepository> { SyncingDayPlanRepository(get(), get(), get(), get()) }
    single<CompletionRepository> { SyncingCompletionRepository(get(), get(), get(), get()) }

    // spec 007. The binding is never nullable: a build with no Supabase configuration
    // still gets a real RemoteDataSource, just one that reports Unreachable (FR-003).
    single { SyncScheduler(androidContext()) }
    single { Outbox(get(), get(), get()) }
    single { accountScopeDaoOf(get()) }
    single { AccountScope(get(), get()) }
    single<RemoteDataSource> { createRemoteDataSource() }
    single { LeaderboardRefresh(get(), get(), get()) }
    single<ParticipationRepository> { RoomParticipationRepository(get(), get()) }
    single<LeaderboardRepository> { RoomLeaderboardRepository(get(), get(), get(), get(), get()) }
    single<HonorBoardRepository> { RoomHonorBoardRepository(get(), get()) }
    single { SyncEngine(get(), get(), get(), get(), get(), get(), ::endSupabaseSession) }
    single { Backfill(get(), get(), get(), get()) }
    single<SyncRepository> { OutboxSyncRepository(get(), get(), get(), scheduler = get()) }
    single<AccountRepository> { createAccountRepository(get(), get(), get(), get(), get()) }
    single<CataloguePublicationRepository> { RemoteCataloguePublicationRepository(get(), get(), get()) }
    factory { RequestSignInCode(get()) }
    factory { ConfirmSignInCode(get(), get()) }
    factory { SignOut(get(), get()) }
    factory { UpdateDisplayName(get()) }
    worker { SyncWorker(get(), get(), get(), get(), get()) }
}

val appModule = module {
    viewModel { TodayViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { WeekViewModel(get(), get(), get(), get()) }
    viewModel { (date: LocalDate) -> DaySummaryViewModel(get<GetDayDetail>(), date) }
    viewModel { HistoryViewModel(get(), get()) }
    viewModel { InsightsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SignInViewModel(get(), get(), get(), isSupabaseConfigured()) }
    viewModel { SyncStatusViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get()) }
    viewModel { LeaderboardViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}

val mizanModules = listOf(domainModule, dataModule, appModule)
