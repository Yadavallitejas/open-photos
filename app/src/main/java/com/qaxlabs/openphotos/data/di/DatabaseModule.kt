package com.qaxlabs.openphotos.data.di

import android.content.Context
import androidx.room.Room
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import com.qaxlabs.openphotos.data.db.VaultDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault_db",
        )
            // v1: no existing users → destructive fallback is safe.
            // Remove this once v2 ships with a proper Migration.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideUploadedItemDao(db: VaultDatabase): UploadedItemDao = db.uploadedItemDao()
}
