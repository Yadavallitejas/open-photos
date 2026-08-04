package com.qaxlabs.openphotos.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.IndexRepository
import com.qaxlabs.openphotos.data.SyncState
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for [HomeScreen].
 *
 * Exposes:
 *  - [syncState] — "Restoring your vault…" / "N items restored" banner (FR-INDEX-2).
 *  - [vaultItemCount] — reactive item count used to label the "View Vault" CTA.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val indexRepository: IndexRepository,
    private val dao: UploadedItemDao,
) : ViewModel() {

    /** Live sync state emitted by [IndexRepository.syncFromRemote]. */
    val syncState: StateFlow<SyncState> = indexRepository.syncState

    /**
     * Live count of items in the vault (Room → reactive).
     * Used to show "View Vault (47)" on the HomeScreen CTA.
     */
    val vaultItemCount: StateFlow<Int> = dao.countFlow()
        .map { it ?: 0 }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )
}
