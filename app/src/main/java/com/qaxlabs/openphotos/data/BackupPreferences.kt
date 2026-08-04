package com.qaxlabs.openphotos.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// One DataStore instance per process (delegate guarantees exactly-once init).
private val Context.backupDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "openphotos_backup_prefs")

/**
 * User-facing preferences for the background auto-backup feature (NG3 / FR-BACKUP-STATUS).
 *
 * Uses a plain (non-encrypted) DataStore — these are toggles, not credentials.
 *
 * Key entries:
 *  - [autoBackupEnabled]     : Feature on/off toggle. Default = false.
 *  - [lastSeenTimestampSecs] : MediaStore DATE_ADDED watermark (Unix seconds).
 *                              The MediaWatcherWorker only processes items
 *                              added after this timestamp, preventing re-queuing
 *                              of already-backed-up content.
 */
@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_AUTO_BACKUP_ENABLED   = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_LAST_SEEN_TS          = longPreferencesKey("last_seen_ts_secs")
    }

    // ── Auto-backup toggle ────────────────────────────────────────────────────

    /**
     * Reactive flow of the auto-backup enabled state.
     * Collect in the Settings ViewModel to keep the switch in sync.
     */
    val autoBackupEnabledFlow: Flow<Boolean> =
        context.backupDataStore.data.map { it[KEY_AUTO_BACKUP_ENABLED] ?: false }

    suspend fun isAutoBackupEnabled(): Boolean =
        context.backupDataStore.data.first()[KEY_AUTO_BACKUP_ENABLED] ?: false

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.backupDataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = enabled }
    }

    // ── Watermark ─────────────────────────────────────────────────────────────

    /**
     * Returns the DATE_ADDED watermark (Unix seconds) for the last media scan.
     * 0L means "never scanned" — the first run will pick up all existing media
     * and dedup via Room's [UploadedItemDao.existsByLocalUri].
     */
    suspend fun getLastSeenTimestamp(): Long =
        context.backupDataStore.data.first()[KEY_LAST_SEEN_TS] ?: 0L

    suspend fun setLastSeenTimestamp(ts: Long) {
        context.backupDataStore.edit { it[KEY_LAST_SEEN_TS] = ts }
    }
}
