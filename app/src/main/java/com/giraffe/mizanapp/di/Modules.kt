package com.giraffe.mizanapp.di

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.createMizanDatabase
import com.giraffe.mizanapp.data.repository.RoomCatalogueRepository
import com.giraffe.mizanapp.data.repository.RoomCompletionRepository
import com.giraffe.mizanapp.data.repository.RoomDayPlanRepository
import com.giraffe.mizanapp.data.seed.CatalogueSeeder
import com.giraffe.mizanapp.data.time.SystemTimeProvider
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.usecase.GetDaySummary
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import com.giraffe.mizanapp.domain.usecase.GetWeekSummary
import com.giraffe.mizanapp.daysummary.DaySummaryViewModel
import com.giraffe.mizanapp.today.TodayViewModel
import com.giraffe.mizanapp.week.WeekViewModel
import java.time.LocalDate
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin is the sole DI framework (constitution, Technology Constraints).
 *
 * This wiring is the one thing in the feature that claims Principle I's
 * test-first exemption — it is configuration, not behaviour.
 */

val domainModule = module {
    single<TimeProvider> { SystemTimeProvider() }
    factory { DayWritePolicy(get()) }
    factory { GetWeekSummary(get(), get(), get(), get()) }
    factory { GetDaySummary(get(), get()) }
    factory { GetStreakSummary(get(), get(), get()) }
}

val dataModule = module {
    // Construction lives in :data so Room stays behind that boundary.
    single<MizanDatabase> { createMizanDatabase(androidContext()) }

    single { CatalogueSeeder(get(), get()) }
    single<CatalogueRepository> { RoomCatalogueRepository(get(), get()) }
    single<DayPlanRepository> { RoomDayPlanRepository(get(), get(), get()) }
    single<CompletionRepository> { RoomCompletionRepository(get(), get(), get(), get()) }
}

val appModule = module {
    viewModel { TodayViewModel(get(), get(), get(), get(), get()) }
    viewModel { WeekViewModel(get(), get(), get(), get()) }
    viewModel { (date: LocalDate) -> DaySummaryViewModel(get(), date) }
}

val mizanModules = listOf(domainModule, dataModule, appModule)
