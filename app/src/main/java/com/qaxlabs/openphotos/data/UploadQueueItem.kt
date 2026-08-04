package com.qaxlabs.openphotos.data

import java.util.UUID

// ── Upload state machine ───────────────────────────────────────────────────────

/**
 * Per-item state in the upload queue.
 *
 * Design constraints:
 *  - [Queued]   → only state where the processor will pick the item up.
 *  - [Oversized]→ set at enqueue time (FR-UPLOAD-5); never enters the processor.
 *  - [Uploading]→ reports progress via [uploadedBytes] / [totalBytes].
 *  - [Done]     → terminal success; includes the Telegram message ID for Room.
 *  - [Failed]   → terminal failure; [retriesLeft] shows auto-retry budget consumed.
 */
sealed class UploadState {
    /** Waiting in line; processor has not yet started this item. */
    object Queued : UploadState()

    /**
     * File exceeds the 2 GB per-file ceiling (free account).
     * Flagged before upload starts — FR-UPLOAD-5.
     */
    object Oversized : UploadState()

    /** Currently being transferred to Telegram. */
    data class Uploading(
        val uploadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : UploadState() {
        /** 0.0–1.0 progress fraction; 0 when total is unknown. */
        val fraction: Float
            get() = if (totalBytes > 0L)
                (uploadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            else 0f
        val isIndeterminate: Boolean get() = totalBytes == 0L
    }

    /** Upload complete; [telegramMessageId] recorded in Room. */
    data class Done(val telegramMessageId: Long) : UploadState()

    /**
     * Upload failed.
     * [retriesLeft] == 0 means the automatic retry budget is exhausted.
     * The UI surfaces a "tap to retry" affordance at this point.
     */
    data class Failed(val reason: String, val retriesLeft: Int = 0) : UploadState()
}

// ── Queue item ────────────────────────────────────────────────────────────────

/**
 * An item in the upload queue, identified by a random [id] so that state
 * updates can be applied by ID without walking the whole list.
 */
data class UploadQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val mediaItem: MediaItem,
    val state: UploadState = UploadState.Queued,
)
