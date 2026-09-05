package com.giraffe.mizanapp.notifications

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.PlannedTask
import com.giraffe.mizanapp.domain.notification.NotificationAnchor
import com.giraffe.mizanapp.domain.notification.NotificationContent
import com.giraffe.mizanapp.domain.notification.NotificationPresenter
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val TODAY: LocalDate = LocalDate.of(2026, 9, 5)

private fun task(slug: String, sectionId: String = "asr", max: Int = 1) = PlannedTask(
    id = slug,
    dayPlanId = "plan",
    taskSlug = slug,
    sectionId = sectionId,
    sectionLabel = sectionId,
    sectionOrder = 0,
    displayPosition = 0,
    label = slug,
    points = 1,
    maxOccurrencesPerDay = max,
)

private fun plan(vararg tasks: PlannedTask) = DayPlan(
    id = "plan",
    date = TODAY,
    catalogueVersion = 1,
    hijriLabel = "1",
    availablePoints = tasks.sumOf { it.availablePoints },
    plannedTasks = tasks.toList(),
    origin = PlanOrigin.OPENED,
)

private fun completion(slug: String) = Completion(
    id = slug,
    dayPlanId = "plan",
    taskSlug = slug,
    creditedDate = TODAY,
    pointsAwarded = 1,
    recordedAt = Instant.EPOCH,
)

private class FakePlans(plan: DayPlan?) : DayPlanRepository {
    val flow = MutableStateFlow(plan)
    override suspend fun planFor(date: LocalDate) = flow.value
    override suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome = throw NotImplementedError()
    override fun observePlan(date: LocalDate): Flow<DayPlan?> = flow
    override suspend fun plansBetween(start: LocalDate, end: LocalDate): List<DayPlan> = throw NotImplementedError()
    override suspend fun earliestPlanDate(): LocalDate? = throw NotImplementedError()
}

private class FakeCompletions : CompletionRepository {
    val flow = MutableStateFlow<List<Completion>>(emptyList())
    override suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome = throw NotImplementedError()
    override suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome = throw NotImplementedError()
    override fun observeCompletions(date: LocalDate): Flow<List<Completion>> = flow
    override suspend fun liveCount(date: LocalDate, taskSlug: String): Int = throw NotImplementedError()
    override suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion> = throw NotImplementedError()
    override fun observeConsistencyDates(): Flow<List<LocalDate>> = throw NotImplementedError()
}

private class FakePresenter : NotificationPresenter {
    val withdrawn = mutableListOf<String>()
    override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) = Unit
    override suspend fun withdraw(anchorKey: String) { withdrawn += anchorKey }
    override fun hasPermission(): Boolean = true
}

class NotificationReconcilerTest {

    @Test
    fun recordingTheLastOutstandingTaskInASectionWithdrawsThatSectionsNudge() = runTest(UnconfinedTestDispatcher()) {
        val plans = FakePlans(plan(task("fajr-fardh")))
        val completions = FakeCompletions()
        val presenter = FakePresenter()
        val reconciler = NotificationReconciler(plans, completions, presenter, backgroundScope, TODAY)
        reconciler.start()

        completions.flow.value = listOf(completion("fajr-fardh"))
        testScheduler.advanceUntilIdle()

        assertTrue(presenter.withdrawn.contains("PRAYER:$TODAY:asr"))
    }

    @Test
    fun recordingATaskInADifferentSectionWithdrawsNothing() = runTest(UnconfinedTestDispatcher()) {
        val plans = FakePlans(
            plan(
                task("fajr-fardh", sectionId = "fajr"),
                task("asr-fardh", sectionId = "asr"),
            ),
        )
        val completions = FakeCompletions()
        val presenter = FakePresenter()
        val reconciler = NotificationReconciler(plans, completions, presenter, backgroundScope, TODAY)
        reconciler.start()

        completions.flow.value = listOf(completion("fajr-fardh"))
        testScheduler.advanceUntilIdle()

        assertFalse(presenter.withdrawn.any { it.contains(":asr") })
    }

    @Test
    fun recordingAnyTaskWithdrawsThePostedStreakReminder() = runTest(UnconfinedTestDispatcher()) {
        val plans = FakePlans(plan(task("fajr-fardh")))
        val completions = FakeCompletions()
        val presenter = FakePresenter()
        val reconciler = NotificationReconciler(plans, completions, presenter, backgroundScope, TODAY)
        reconciler.start()

        completions.flow.value = listOf(completion("fajr-fardh"))
        testScheduler.advanceUntilIdle()

        assertTrue(presenter.withdrawn.contains("STREAK:$TODAY"))
    }

    @Test
    fun withdrawingANudgeNeverPostedDoesNotThrow() = runTest(UnconfinedTestDispatcher()) {
        val plans = FakePlans(plan(task("fajr-fardh")))
        val completions = FakeCompletions()
        val presenter = FakePresenter()
        val reconciler = NotificationReconciler(plans, completions, presenter, backgroundScope, TODAY)
        reconciler.start()

        completions.flow.value = listOf(completion("fajr-fardh"))
        testScheduler.advanceUntilIdle()
        // No exception thrown is the assertion.
    }
}
