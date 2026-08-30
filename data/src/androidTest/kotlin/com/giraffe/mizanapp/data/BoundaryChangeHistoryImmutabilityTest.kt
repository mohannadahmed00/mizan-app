package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.db.entities.BoundaryStateEntity
import com.giraffe.mizanapp.data.prayer.FakeLocationSource
import com.giraffe.mizanapp.data.prayer.FakePrayerTimes
import com.giraffe.mizanapp.data.time.BoundaryStateStore
import com.giraffe.mizanapp.domain.day.scoreDay
import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.HijriLabel
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FR-025 — the constitution's required historical-immutability test for any increment touching
 * persistence. Nothing already closed may change when the boundary regime changes underneath it:
 * `DayPlanRepository` has no update method, and the clamp changes only future resolution, so this
 * is a verification test, not new behaviour (T060).
 */
class BoundaryChangeHistoryImmutabilityTest : DbTestBase() {

    // A closed week: 2026-03-07 (Sat) through 2026-03-13 (Fri), entirely before "today" (2026-03-14).
    private val recordedDates = listOf(
        LocalDate.parse("2026-03-08"),
        LocalDate.parse("2026-03-09"),
        LocalDate.parse("2026-03-10"),
    )

    private data class DaySnapshot(
        val earned: Int,
        val available: Int,
        val percentage: Int,
        val hijriLabel: String,
    )

    private suspend fun seedHistory() {
        catalogue.seedIfNeeded()
        for (date in recordedDates) {
            time.setDate(date)
            dayPlans.ensurePlanFor(date)
            val plan = dayPlans.planFor(date)!!
            completions.record(date, plan.plannedTasks.first().taskSlug)
        }
    }

    private suspend fun snapshotFor(date: LocalDate): DaySnapshot {
        val plan = dayPlans.planFor(date)!!
        val records = completions.observeCompletions(date).first()
        val score = scoreDay(plan, records)
        val percentage = if (score.available == 0) 0 else (score.earned * 100) / score.available
        return DaySnapshot(score.earned, score.available, percentage, plan.hijriLabel)
    }

    private suspend fun switchToMaghribWithCoordinates(): BoundaryStateStore {
        val locationSource = FakeLocationSource(Coordinates(30.0, 31.2))
        val prayerTimes = FakePrayerTimes().apply { setDefaultMaghribLocalTime(LocalTime.of(18, 0)) }
        db.boundaryStateDao().upsert(
            BoundaryStateEntity(
                latitude = 30.0,
                longitude = 31.2,
                zoneIdWhenObtained = time.zone().id,
                obtainedAt = time.now().toEpochMilli(),
                lastResolvedDate = null,
                lastResolvedRegime = null,
            ),
        )
        val store = BoundaryStateStore(db.boundaryStateDao(), locationSource, prayerTimes)
        store.refresh(time.now(), time.zone())
        return store
    }

    @Test
    fun figuresAreByteIdenticalAfterSwitchingToMaghrib() = runTest {
        seedHistory()
        val before = recordedDates.map { snapshotFor(it) }
        val weeklyTotalBefore = before.sumOf { it.earned }

        val store = switchToMaghribWithCoordinates()
        assertEquals(BoundaryRegime.Maghrib, store.current().regime)

        val after = recordedDates.map { snapshotFor(it) }
        assertEquals(before, after)
        assertEquals(weeklyTotalBefore, after.sumOf { it.earned })
    }

    @Test
    fun completionCreditedDateIsNeverRewritten() = runTest {
        seedHistory()
        val creditedDatesBefore = recordedDates.associateWith { date ->
            db.completionDao().liveByDate(date.toString()).map { it.creditedDate }
        }

        switchToMaghribWithCoordinates()

        val creditedDatesAfter = recordedDates.associateWith { date ->
            db.completionDao().liveByDate(date.toString()).map { it.creditedDate }
        }
        assertEquals(creditedDatesBefore, creditedDatesAfter)
    }

    @Test
    fun hijriLabelsAreStillComputedLocallyForMaghribBoundaryDays() = runTest {
        catalogue.seedIfNeeded()
        switchToMaghribWithCoordinates()

        val newDate = LocalDate.parse("2026-03-20")
        time.setDate(newDate)
        dayPlans.ensurePlanFor(newDate)
        val plan = dayPlans.planFor(newDate)!!

        assertEquals(HijriLabel.forDate(newDate), plan.hijriLabel)
    }

    @Test
    fun changingTheRegionConventionMappingLeavesClosedDaysUnchanged() = runTest {
        seedHistory()
        val before = recordedDates.map { snapshotFor(it) }

        // A different convention mapping is a different PrayerTimesProvider instance entirely --
        // closed days read from storage, never from a live calculation, so this changes nothing.
        val differentPrayerTimes = FakePrayerTimes().apply { setDefaultMaghribLocalTime(LocalTime.of(19, 30)) }
        val store = BoundaryStateStore(
            db.boundaryStateDao(),
            FakeLocationSource(Coordinates(24.7, 46.7)),
            differentPrayerTimes,
        )
        store.refresh(time.now(), time.zone())

        val after = recordedDates.map { snapshotFor(it) }
        assertEquals(before, after)
    }
}
