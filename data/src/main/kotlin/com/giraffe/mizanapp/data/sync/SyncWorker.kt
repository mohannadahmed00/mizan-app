package com.giraffe.mizanapp.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.giraffe.mizanapp.domain.repository.CataloguePublicationRepository

/**
 * Drains the outbox and pulls remote changes with the app closed — what makes
 * SC-003's one-minute delivery real without the user opening anything.
 *
 * The catalogue pull is wrapped so that any outcome besides `Added` is logged
 * and ignored rather than short-circuiting the record sync (T114) — a
 * catalogue problem must never block a user's own record from reaching the
 * account.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val engine: SyncEngine,
    private val catalogue: CataloguePublicationRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        catalogue.pullIfNewer()
        val outcome = engine.drain()
        engine.pull()
        return if (outcome == DrainOutcome.StoppedUnreachable) Result.retry() else Result.success()
    }
}
