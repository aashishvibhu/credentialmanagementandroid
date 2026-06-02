package com.github.aashishvibhu.credentialmanagement.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME_PERIODIC   = "sync_periodic"
        const val WORK_NAME_IMMEDIATE  = "sync_immediate"
    }

    override suspend fun doWork(): Result {
        return try {
            syncManager.sync()
            Result.success()
        } catch (e: IllegalStateException) {
            // Not signed in — no point retrying until the user signs in
            Result.failure()
        } catch (e: Exception) {
            // Transient error (network, etc.) — retry with backoff, up to 3 attempts
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
