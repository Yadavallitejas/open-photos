package com.qaxlabs.openphotos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists a record of every file successfully uploaded to Telegram Saved
 * Messages.
 *
 * This is the local ground truth for "what's been backed up from this device."
 * The Telegram-side JSON index (FR-INDEX) is a future layer that reads from
 * this table; it is not implemented in this version.
 *
 * tech_stack.md §4: Room, one table per domain entity, no joins in v1.
 */
@Entity(tableName = "uploaded_items")
data class UploadedItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Stable UUID assigned at enqueue time. This ID appears in the JSON index
     * as [VaultIndexEntry.id] and survives reinstalls via the remote index.
     */
    val indexId: String,

    /** Original MediaStore content URI, e.g. content://media/external/images/1234. */
    val localUri: String,

    /** File display name from MediaStore (e.g. "IMG_20240601_120000.jpg"). */
    val displayName: String,

    /** File size in bytes at time of upload. */
    val sizeBytes: Long,

    /** MIME type, e.g. "image/jpeg" or "video/mp4". */
    val mimeType: String,

    /** SHA-256 checksum for duplicate detection (DRD §9). Format: "sha256:<hex>". */
    val sha256: String,

    /** Original photo/video date — epoch millis (MediaStore DATE_ADDED * 1000). */
    val takenAt: Long,

    /** Telegram chat ID where the file was sent (= Saved Messages chat). */
    val telegramChatId: Long,

    /** Telegram message ID of the Document message containing this file. */
    val telegramMessageId: Long,

    /** Wall-clock milliseconds when the upload completed (System.currentTimeMillis()). */
    val uploadedAt: Long,
)
