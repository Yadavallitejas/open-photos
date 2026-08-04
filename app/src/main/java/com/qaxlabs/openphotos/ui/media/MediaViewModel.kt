package com.qaxlabs.openphotos.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.MediaItem
import com.qaxlabs.openphotos.data.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [MediaScreen].
 *
 * Owns:
 *  - The loaded [MediaItem] list (loaded once, refreshed on explicit [refresh]).
 *  - The multi-selection set (Set<Long> of MediaItem IDs).
 *  - Derived selection stats: count and total size in bytes (FR-MEDIA-3).
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This class does NOT import or reference anything from MediaStore or
 * ContentResolver directly — that is [MediaRepository]'s responsibility.
 */
@HiltViewModel
class MediaViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // ── Derived selection stats (FR-MEDIA-3) ──────────────────────────────────

    val selectionCount: Int
        get() = _selectedIds.value.size

    val selectionSizeBytes: Long
        get() = _mediaItems.value
            .filter { it.id in _selectedIds.value }
            .sumOf { it.sizeBytes }

    val selectedItems: List<MediaItem>
        get() = _mediaItems.value.filter { it.id in _selectedIds.value }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Loads all media items. Should be called once permissions are confirmed
     * (the screen handles the permission gate; this is only called after grant).
     */
    fun loadMedia() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _mediaItems.value = repository.queryAll()
            _isLoading.value = false
        }
    }

    /** Adds or removes [itemId] from the selection set. */
    fun toggleSelection(itemId: Long) {
        _selectedIds.update { current ->
            if (itemId in current) current - itemId else current + itemId
        }
    }

    /** Selects all loaded items. */
    fun selectAll() {
        _selectedIds.value = _mediaItems.value.map { it.id }.toSet()
    }

    /** Clears all selections. */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }
}
