package com.giraffe.mizanapp.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.giraffe.mizanapp.domain.notification.DeliveryRecord
import com.giraffe.mizanapp.domain.notification.DeliveryState
import com.giraffe.mizanapp.domain.notification.NotificationPresenter
import com.giraffe.mizanapp.domain.notification.NotificationScheduler
import com.giraffe.mizanapp.domain.notification.NotificationVerdict
import com.giraffe.mizanapp.domain.notification.buildNotificationPlan
import com.giraffe.mizanapp.domain.notification.evaluateAnchor
import com.giraffe.mizanapp.domain.notification.anchorKey
import com.giraffe.mizanapp.domain.notification.weekCloseInstant
import com.giraffe.mizanapp.domain.prayer.PrayerTimesOutcome
import com.giraffe.mizanapp.domain.prayer.PrayerTimesProvider
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.time.BoundaryStatus
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import kotlinx.coroutines.flow.first

/** Worker entry point. Its dependencies are supplied by Koin when notification processing is wired. */
class NotificationWorker(context: Context, params: WorkerParameters, private val time: TimeProvider, private val boundaryStatus: BoundaryStatus, private val prayers: PrayerTimesProvider, private val plans: DayPlanRepository, private val completions: CompletionRepository, private val streaks: GetStreakSummary, private val preferences: NotificationPreferencesStore, private val deliveries: DeliveryStore, private val scheduler: NotificationScheduler, private val presenter: NotificationPresenter) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val now = time.now(); val zone = time.zone()
        boundaryStatus.refresh(now, zone)
        val boundary = boundaryStatus.current()
        val plan = plans.planFor(boundary.resolvedDate)
        val live = completions.liveBetween(boundary.resolvedDate, boundary.resolvedDate)
        val prayerTimes = boundary.coordinates?.let { (prayers.timesFor(boundary.resolvedDate, it, zone) as? PrayerTimesOutcome.Calculated)?.times }
        val planResult = buildNotificationPlan(now, zone, boundary, prayerTimes, plan, live, streaks().first(), preferences.preferences(), weekCloseInstant(boundary), deliveries.records())
        inputData.getString(INPUT_ANCHOR_KEY)?.let { key -> planResult.anchors.firstOrNull { it.anchorKey == key }?.let { anchor ->
            when (val verdict = evaluateAnchor(anchor, now, zone, boundary, plan, live, streaks().first(), preferences.preferences(), null, deliveries.records().firstOrNull { it.anchorKey == key }, presenter.hasPermission())) {
                is NotificationVerdict.Post -> { presenter.post(anchor, verdict.content); deliveries.record(DeliveryRecord(key, anchor.category, DeliveryState.DELIVERED, null, now, null)) }
                is NotificationVerdict.Discard -> { presenter.withdraw(key); deliveries.record(DeliveryRecord(key, anchor.category, DeliveryState.DISCARDED, verdict.reason, now, null)) }
                is NotificationVerdict.Hold -> deliveries.record(DeliveryRecord(key, anchor.category, DeliveryState.HELD, null, now, verdict.until))
            }
        } }
        scheduler.replaceAll(planResult.anchors)
        scheduler.scheduleRefresh(planResult.refreshAt)
        deliveries.prune(now)
        Result.success()
    }.getOrElse { Result.retry() }
    companion object { const val INPUT_ANCHOR_KEY = "anchorKey" }
}
