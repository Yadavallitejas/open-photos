package com.qaxlabs.openphotos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.BackupPreferences
import com.qaxlabs.openphotos.data.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the auto-backup toggle in [SettingsScreen].
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * Does NOT call TDLib or Room directly. Delegates to [BackupPreferences]
 * (DataStore) and [WorkScheduler] (WorkManager).
 */
@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val backupPreferences: BackupPreferences,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    /**
     * Reactive auto-backup enabled state.
     * Collected by [SettingsScreen] to keep the Switch in sync with DataStore.
     */
    val autoBackupEnabled: StateFlow<Boolean> =
        backupPreferences.autoBackupEnabledFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    /**
     * Toggles the auto-backup feature and schedules / cancels the
     * WorkManager periodic job accordingly.
     *
     * Called from the Settings Switch's onCheckedChange callback.
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            backupPreferences.setAutoBackupEnabled(enabled)
            if (enabled) {
                workScheduler.schedule()
            } else {
                workScheduler.cancel()
            }
        }
    }
}
