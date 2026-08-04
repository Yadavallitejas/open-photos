package com.qaxlabs.openphotos.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.UploadQueueItem
import com.qaxlabs.openphotos.data.UploadRepository
import com.qaxlabs.openphotos.data.UploadState
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class VaultGalleryItem(
    val indexId: String,
    val localUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val isUploading: Boolean,
    val isBackedUp: Boolean,
    val entity: UploadedItemEntity? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

/**
 * ViewModel for [GalleryScreen] and [GalleryDetailScreen].
 *
 * Combines Room database snapshot with active [UploadRepository] queue items
 * so uploading photos display with the animated Sync Halo in real-time.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val dao: UploadedItemDao,
    private val uploadRepository: UploadRepository,
) : ViewModel() {

    /** Room DB backed-up items, newest first. */
    val backedUpItems: StateFlow<List<UploadedItemEntity>> = dao.getAllFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Upload queue items. */
    val uploadQueue: StateFlow<List<UploadQueueItem>> = uploadRepository.queue

    /** Combined items for grid display: active uploads (with Sync Halo) + backed-up Room items. */
    val combinedItems: StateFlow<List<VaultGalleryItem>> = combine(
        backedUpItems,
        uploadQueue,
    ) { roomItems, queueItems ->
        val roomIndexIds = roomItems.map { it.indexId }.toSet()
        val roomUris = roomItems.map { it.localUri }.toSet()

        // Filter active queue items (not done yet and not already in Room)
        val activeUploads = queueItems
            .filter { item ->
                (item.state is UploadState.Queued || item.state is UploadState.Uploading) &&
                        item.id !in roomIndexIds &&
                        item.mediaItem.uri.toString() !in roomUris
            }
            .map { queueItem ->
                VaultGalleryItem(
                    indexId = queueItem.id,
                    localUri = queueItem.mediaItem.uri.toString(),
                    displayName = queueItem.mediaItem.displayName,
                    sizeBytes = queueItem.mediaItem.sizeBytes,
                    mimeType = queueItem.mediaItem.mimeType,
                    isUploading = true,
                    isBackedUp = false,
                    entity = null,
                )
            }

        val backedUpGalleryItems = roomItems.map { entity ->
            VaultGalleryItem(
                indexId = entity.indexId,
                localUri = entity.localUri,
                displayName = entity.displayName,
                sizeBytes = entity.sizeBytes,
                mimeType = entity.mimeType,
                isUploading = false,
                isBackedUp = true,
                entity = entity,
            )
        }

        // Active uploads first, then backed up items
        activeUploads + backedUpGalleryItems
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun findByIndexId(indexId: String): VaultGalleryItem? =
        combinedItems.value.firstOrNull { it.indexId == indexId }
}
