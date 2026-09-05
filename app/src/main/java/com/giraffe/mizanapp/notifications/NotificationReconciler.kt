package com.giraffe.mizanapp.notifications

import com.giraffe.mizanapp.domain.day.liveCount
import com.giraffe.mizanapp.domain.notification.NotificationPresenter
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Withdraws an already-posted notification the instant its work is done. Never writes to
 * [CompletionRepository] and never decides what to post — that is [NotificationWorker]'s job
 * on the next fire (research R5): this only reacts to what is already recorded.
 */
class NotificationReconciler(
    private val dayPlans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val presenter: NotificationPresenter,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate,
) {
    constructor(
        dayPlans: DayPlanRepository,
        completions: CompletionRepository,
        presenter: NotificationPresenter,
        scope: CoroutineScope,
        today: LocalDate,
    ) : this(dayPlans, completions, presenter, scope, { today })

    constructor(
        dayPlans: DayPlanRepository,
        completions: CompletionRepository,
        presenter: NotificationPresenter,
        scope: CoroutineScope,
        time: TimeProvider,
    ) : this(dayPlans, completions, presenter, scope, time::today)

    fun start() {
        val date = today()
        scope.launch {
            combine(dayPlans.observePlan(date), completions.observeCompletions(date)) { plan, records -> plan to records }
                .distinctUntilChanged()
                .collect { (plan, records) ->
                    if (plan == null) return@collect
                    if (records.isNotEmpty()) presenter.withdraw("STREAK:$date")
                    plan.sectionsInOrder().forEach { (sectionId, tasks) ->
                        val complete = tasks.all { liveCount(records, it.taskSlug) >= it.maxOccurrencesPerDay }
                        if (complete) presenter.withdraw("PRAYER:$date:$sectionId")
                    }
                }
        }
    }
}
