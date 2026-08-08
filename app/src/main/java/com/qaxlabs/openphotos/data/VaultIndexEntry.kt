package com.qaxlabs.openphotos.data

import kotlinx.serialization.Serializable

/**
 * One file entry in the vault index (PRD §7).
 *
 * Field contract:
 *  - [id]         Stable UUID generated at enqueue time; links to [UploadedItemEntity.indexId].
 *  - [messageId]  Telegram message ID of the Document that holds the file.
 *  - [checksum]   "sha256:<hex>" — for future duplicate detection (DRD §9).
 *  - [takenAt]    ISO-8601 of original file date (MediaStore DATE_ADDED).
 *  - [uploadedAt] ISO-8601 of the moment the upload completed.
 *  - [chatId]     Saved Messages chat ID; 0 for entries created before FR-GALLERY-4.
 *
 * Kept minimal and aligned with the PRD §7 schema. Extra fields (albums,
 * captions, GPS) are deferred to v2.
 */
@Serializable
data class VaultIndexEntry(
    val id: String,
    val messageId: Long,
    val filename: String,
    val sizeBytes: Long,
    val checksum: String,
    val takenAt: String,
    val uploadedAt: String,
    val mimeType: String,
    val localUri: String,
    /** Saved Messages chat ID. Preserved for deletion (FR-GALLERY-4). 0 = unknown. */
    val chatId: Long = 0L,
    /** Remote file ID of the attached Document thumbnail (FR-UPLOAD-6). Empty if none. */
    val thumbnailRemoteId: String = "",
)
