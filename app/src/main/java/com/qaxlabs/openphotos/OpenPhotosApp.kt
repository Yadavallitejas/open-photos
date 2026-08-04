package com.qaxlabs.openphotos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry-point. The @HiltAndroidApp annotation triggers Hilt's
 * code generation and installs the application-level component, which is the
 * root of the dependency graph for all other components.
 *
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory],
 * which enables @HiltWorker / @AssistedInject in [MediaWatcherWorker].
 * This replaces the default WorkManager initialisation that would otherwise
 * be registered via the manifest meta-data provider.
 */
@HiltAndroidApp
class OpenPhotosApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

