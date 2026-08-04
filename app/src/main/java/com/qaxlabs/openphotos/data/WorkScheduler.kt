package com.qaxlabs.openphotos.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralises WorkManager scheduling for the auto-backup feature.
 *
 * Call [schedule] when the user turns the feature on, [cancel] when they turn
 * it off. Using [ExistingPeriodicWorkPolicy.KEEP] means calling [schedule]
 * repeatedly (e.g. on every app launch) does not reset the timer.
 *
 * Constraints:
 *   - CONNECTED network required — no uploads on offline/metered unless the
 *     user has specifically chosen so (deferred setting for v2).
 *
 * Interval: 15 min — the Android minimum for periodic WorkManager jobs.
 * The OS may defer this further under Doze; this is acceptable per the
 * plan (goal is "queued without opening the app", not "queued within 15 min").
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<MediaWatcherWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            MediaWatcherWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,   // don't reset timer on repeated calls
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(MediaWatcherWorker.WORK_NAME)
    }
}
