package com.qaxlabs.openphotos.data.di

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds Hilt's [HiltWorkerFactory] as the [WorkerFactory] implementation.
 *
 * This is required so WorkManager can inject dependencies into workers
 * annotated with @HiltWorker. The binding is used in [OpenPhotosApp.onCreate]
 * where WorkManager is initialised with a custom configuration that passes
 * this factory instead of the default one.
 *
 * See: https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    abstract fun bindWorkerFactory(factory: HiltWorkerFactory): WorkerFactory
}
