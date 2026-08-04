package com.qaxlabs.openphotos.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the application-level [CoroutineScope] whose lifetime matches
 * the process. Inject this wherever you need a scope that outlives any single
 * ViewModel or screen — specifically [TelegramAuthRepository] which must keep
 * listening to TDLib updates for the entire app lifetime.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides a [CoroutineScope] tied to the singleton component lifecycle.
     * Uses [SupervisorJob] so a failure in one child doesn't cancel siblings,
     * and [Dispatchers.Default] to keep CPU-bound work off the main thread.
     *
     * Note: [TelegramClient] and [SecureStore] are @Singleton + @Inject
     * constructor — Hilt provides them automatically without explicit @Provides.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
