package com.qaxlabs.openphotos.ui.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.ThumbnailRepository
import com.qaxlabs.openphotos.data.UploadQueueItem
import com.qaxlabs.openphotos.data.UploadRepository
import com.qaxlabs.openphotos.data.UploadState
import com.qaxlabs.openphotos.data.ViewerFileState
import com.qaxlabs.openphotos.data.ViewerRepository
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val thumbnailPath: String? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

/** Result of an explicit "Download to gallery" action. */
sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Failure(val reason: String) : DownloadResult
}

/**
 * ViewModel for [GalleryScreen] and [GalleryDetailScreen].
 *
 * Combines Room database snapshot with active [UploadRepository] queue items,
 * thumbnail preview paths resolved via [ThumbnailRepository], and full-resolution
 * viewer states via [ViewerRepository].
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val dao: UploadedItemDao,
    private val uploadRepository: UploadRepository,
    private val thumbnailRepository: ThumbnailRepository,
    private val viewerRepository: ViewerRepository,
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

    /** Map of indexId -> local thumbnail preview path. */
    private val _thumbnailMap = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Per-item viewer file state (full-resolution download). */
    val viewerStateMap: StateFlow<Map<String, ViewerFileState>> = viewerRepository.stateMap

    /** One-shot result for the explicit Download-to-gallery action. */
    private val _downloadResult = MutableSharedFlow<DownloadResult>(extraBufferCapacity = 1)
    val downloadResult: SharedFlow<DownloadResult> = _downloadResult.asSharedFlow()

    init {
        // Asynchronously fetch/cache thumbnail previews for all Room entities
        viewModelScope.launch {
            backedUpItems.collect { entities ->
                entities.forEach { entity ->
                    if (!_thumbnailMap.value.containsKey(entity.indexId)) {
                        launch {
                            val thumbFile = thumbnailRepository.getOrFetchThumbnailFile(entity)
                            if (thumbFile != null && thumbFile.exists()) {
                                _thumbnailMap.update { current ->
                                    current + (entity.indexId to thumbFile.absolutePath)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Combined items for grid display: active uploads (with Sync Halo) + backed-up Room items. */
    val combinedItems: StateFlow<List<VaultGalleryItem>> = combine(
        backedUpItems,
        uploadQueue,
        _thumbnailMap,
    ) { roomItems, queueItems, thumbs ->
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
                    thumbnailPath = null,
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
                thumbnailPath = thumbs[entity.indexId],
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

    /**
     * Triggers a background full-resolution fetch for [indexId].
     * Safe to call multiple times — idempotent while already loading.
     */
    fun fetchFullResolution(indexId: String) {
        val entity = findByIndexId(indexId)?.entity ?: return
        viewModelScope.launch {
            viewerRepository.fetchFullResolution(entity)
        }
    }

    /**
     * Saves the full-resolution file (must already be [ViewerFileState.Ready])
     * to the device gallery via MediaStore. Emits a [DownloadResult] on
     * [downloadResult].
     */
    fun downloadToGallery(indexId: String) {
        val entity = findByIndexId(indexId)?.entity ?: return
        val state = viewerRepository.stateFor(indexId)
        val localPath = (state as? ViewerFileState.Ready)?.localPath ?: return
        viewModelScope.launch {
            val uri: Uri? = viewerRepository.saveToMediaStore(entity, localPath)
            if (uri != null) {
                _downloadResult.tryEmit(DownloadResult.Success)
            } else {
                _downloadResult.tryEmit(DownloadResult.Failure("Failed to save to gallery"))
            }
        }
    }
}
