package io.github.duddmeister.weather.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.duddmeister.weather.container
import io.github.duddmeister.weather.core.AppLog
import java.util.concurrent.TimeUnit

/** Keeps forecasts (and therefore widgets) fresh in the background. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val retryUntilOnline = inputData.getBoolean(KEY_RETRY, false)
        val ok = applicationContext.container.refresher.refreshBackground(force = inputData.getBoolean(KEY_FORCE, false))
        AppLog.i(TAG, "Background refresh (${tags.joinToString()}) ok=$ok attempt=$runAttemptCount")
        return if (!ok && retryUntilOnline && runAttemptCount < 5) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_FORCE = "force"
        const val KEY_RETRY = "retry"
        private const val TAG = "RefreshWorker"
    }
}

/** Checks GitHub for a newer release and posts a notification about it. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.updates.checkInBackground()
        return Result.success()
    }
}

object WorkScheduler {
    private const val PERIODIC_REFRESH = "weather-refresh"
    private const val RETRY_REFRESH = "weather-refresh-retry"
    private const val STALE_REFRESH = "weather-refresh-stale"
    private const val UPDATE_CHECK = "update-check"

    /** (Re)schedules periodic refresh; [intervalMinutes] of 0 turns it off. */
    fun schedulePeriodicRefresh(context: Context, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (intervalMinutes <= 0) {
            workManager.cancelUniqueWork(PERIODIC_REFRESH)
            return
        }
        // No network constraint on purpose: offline runs still re-render widgets from the forecast timeline.
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .addTag("periodic")
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_REFRESH, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** One refresh as soon as the device is online again (after a failed attempt). */
    fun refreshWhenOnline(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(RefreshWorker.KEY_FORCE to true, RefreshWorker.KEY_RETRY to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("retry")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(RETRY_REFRESH, ExistingWorkPolicy.REPLACE, request)
    }

    /** A refresh soon (used when the launcher asks widgets to update); skips locations that are already fresh. */
    fun refreshSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("stale")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(STALE_REFRESH, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleUpdateChecks(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UPDATE_CHECK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
