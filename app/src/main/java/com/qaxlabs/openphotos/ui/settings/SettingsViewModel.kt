package com.qaxlabs.openphotos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.SecureStore
import com.qaxlabs.openphotos.data.TelegramAuthRepository
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [SettingsScreen] (FR-SETTINGS).
 *
 * Exposes API credentials, storage metrics (count & total size), and updating credentials.
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * Does NOT reference TDLib classes directly. Interacts via repositories & DAOs.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStore: SecureStore,
    private val authRepository: TelegramAuthRepository,
    private val dao: UploadedItemDao,
) : ViewModel() {

    /** Stored Telegram api_id (masked in UI). */
    val apiId: StateFlow<Int?> = secureStore.apiIdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Stored Telegram api_hash (masked in UI). */
    val apiHash: StateFlow<String?> = secureStore.apiHashFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Total items backed up in Room cache (FR-SETTINGS-3). */
    val backedUpCount: StateFlow<Int> = dao.countFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /** Total storage bytes used in Saved Messages (FR-SETTINGS-3). */
    val totalSizeBytes: StateFlow<Long> = dao.totalSizeBytesFlow()
        .map { it ?: 0L }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0L,
        )

    /**
     * Updates api_id and api_hash in SecureStore and re-initializes TDLib parameters (FR-SETTINGS-1).
     */
    fun updateCredentials(newApiId: Int, newApiHash: String) {
        viewModelScope.launch {
            authRepository.updateCredentials(newApiId, newApiHash)
        }
    }
}
