package com.giraffe.mizanapp.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.giraffe.domain.usecase.SyncMonthlyHijriDatesUseCase
import org.koin.android.annotation.KoinWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

@KoinWorker
class SyncHijriDatesWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncMonthlyHijriDatesUseCase: SyncMonthlyHijriDatesUseCase,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            syncMonthlyHijriDatesUseCase()
            scheduleNextRun(applicationContext)
            Result.success()
        } catch (_: Exception) {
            handleFailure()
        }
    }

    private fun handleFailure(): Result =
        if (runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            scheduleNextRun(applicationContext)
            Result.failure()
        }


    companion object {
        private const val WORK_NAME = "sync_monthly_hijri_dates"
        private const val MAX_RETRIES = 3

        /**
         * Call once on app startup. Runs immediately on first install.
         * On every later launch this is a no-op, since the chain already
         * has a pending entry under the same unique name (KEEP skips it).
         */
        fun scheduleInitialSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncHijriDatesWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                ).build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Called by the worker itself after each run to schedule the
         * following month's sync. Always replaces, since this is the
         * authoritative "what's next" call.
         */
        private fun scheduleNextRun(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncHijriDatesWorker>()
                .setInitialDelay(delayUntilNextMonthStart(), TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun networkConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        private fun delayUntilNextMonthStart(): Long {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}