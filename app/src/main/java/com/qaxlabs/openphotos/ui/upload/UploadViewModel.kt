package com.qaxlabs.openphotos.ui.upload

import androidx.lifecycle.ViewModel
import com.qaxlabs.openphotos.data.FloodWaitState
import com.qaxlabs.openphotos.data.MediaItem
import com.qaxlabs.openphotos.data.UploadQueueItem
import com.qaxlabs.openphotos.data.UploadRepository
import com.qaxlabs.openphotos.data.UploadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for [UploadScreen].
 *
 * Thin adapter over [UploadRepository]: forwards the queue [StateFlow] to the
 * UI and exposes derived counts. All mutation goes through [UploadRepository].
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This class does NOT import or reference any TDLib class.
 */
@HiltViewModel
class UploadViewModel @Inject constructor(
    private val uploadRepository: UploadRepository,
) : ViewModel() {

    /** Full queue snapshot; each state change emits a new immutable list. */
    val queue: StateFlow<List<UploadQueueItem>> = uploadRepository.queue

    /**
     * FR-BACKUP-STATUS-2: flood-wait pause state.
     * [FloodWaitState.Waiting] carries [resumesAt] epoch-millis so the UI
     * can render a human-readable countdown.
     */
    val floodWaitState: StateFlow<FloodWaitState> = uploadRepository.floodWaitState

    // ── Derived stats ─────────────────────────────────────────────────────────

    val doneCount: Int
        get() = uploadRepository.queue.value.count { it.state is UploadState.Done }

    val totalCount: Int
        get() = uploadRepository.queue.value.size

    val oversizedCount: Int
        get() = uploadRepository.queue.value.count { it.state is UploadState.Oversized }

    val failedCount: Int
        get() = uploadRepository.queue.value.count { it.state is UploadState.Failed }

    val hasActiveUploads: Boolean
        get() = uploadRepository.queue.value.any { it.state is UploadState.Uploading }

    val isAllFinished: Boolean
        get() = uploadRepository.queue.value.isNotEmpty() &&
                uploadRepository.queue.value.none { it.state is UploadState.Queued || it.state is UploadState.Uploading }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Enqueues [items] for upload. Typically called by the MediaScreen before
     * navigating here, using the items the user selected.
     *
     * FR-UPLOAD-5: [UploadRepository.enqueue] immediately flags oversized items
     * before the processor loop starts.
     */
    fun startUpload(items: List<MediaItem>) {
        uploadRepository.enqueue(items)
    }

    /**
     * Clears the queue so the user can start a fresh selection.
     * Only safe to call when [isAllFinished] is true.
     */
    fun clearQueue() {
        uploadRepository.clearQueue()
    }

    /**
     * Retries a failed item in the upload queue (FR-UPLOAD-4).
     */
    fun retryUpload(itemId: String) {
        uploadRepository.retryItem(itemId)
    }
}
