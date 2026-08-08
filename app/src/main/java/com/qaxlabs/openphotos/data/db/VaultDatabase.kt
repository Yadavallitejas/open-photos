package com.qaxlabs.openphotos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Application Room database — the local vault cache.
 *
 * Version history:
 *   v1 — [UploadedItemEntity]: records every file successfully uploaded to
 *         Telegram Saved Messages.
 *   v2 — (planned) Telegram-side index cache (FR-INDEX-2).
 *   v3 — (planned) Gallery thumbnail cache metadata (FR-GALLERY-2).
 *
 * Migration strategy: destructive for v1 (no existing users). From v2 onward,
 * use auto-migrations or explicit Migration objects.
 */
@Database(
    entities = [UploadedItemEntity::class],
    version = 3,          // v3: adds thumbnailRemoteId column (FR-UPLOAD-6)
    exportSchema = true,
)

abstract class VaultDatabase : RoomDatabase() {
    abstract fun uploadedItemDao(): UploadedItemDao
}
