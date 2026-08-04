package com.qaxlabs.openphotos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadedItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UploadedItemEntity)

    /** Batch insert — used by [IndexRepository] when hydrating from the remote index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<UploadedItemEntity>)

    /** Wipes the cache in preparation for remote-index hydration (FR-INDEX-4: remote wins). */
    @Query("DELETE FROM uploaded_items")
    suspend fun clearAll()

    /** All uploaded items, newest first — reactive flow for gallery UI. */
    @Query("SELECT * FROM uploaded_items ORDER BY uploadedAt DESC")
    fun getAllFlow(): Flow<List<UploadedItemEntity>>

    /**
     * One-shot (non-reactive) snapshot of all items.
     * Used by [IndexRepository.pushIndex] to build the JSON without subscribing
     * to a flow.
     */
    @Query("SELECT * FROM uploaded_items ORDER BY uploadedAt DESC")
    suspend fun getAllOnce(): List<UploadedItemEntity>

    /** Lightweight reactive count — used by HomeScreen and Settings. */
    @Query("SELECT COUNT(*) FROM uploaded_items")
    fun countFlow(): Flow<Int>

    /** Total backed-up bytes — used by Settings FR-SETTINGS-3. */
    @Query("SELECT SUM(sizeBytes) FROM uploaded_items")
    fun totalSizeBytesFlow(): Flow<Long?>

    /** Look up by localUri for duplicate detection (FR-INDEX dedup). */
    @Query("SELECT * FROM uploaded_items WHERE localUri = :uri LIMIT 1")
    suspend fun findByLocalUri(uri: String): UploadedItemEntity?

    /** Look up by indexId (stable cross-device UUID). */
    @Query("SELECT * FROM uploaded_items WHERE indexId = :id LIMIT 1")
    suspend fun findByIndexId(id: String): UploadedItemEntity?

    /**
     * Fast existence check used by [MediaWatcherWorker] to skip items that
     * are already backed up. Avoids loading the full entity for dedup.
     */
    @Query("SELECT COUNT(*) > 0 FROM uploaded_items WHERE localUri = :uri")
    suspend fun existsByLocalUri(uri: String): Boolean

    /** Look up by SHA-256 checksum for duplicate upload detection. */
    @Query("SELECT * FROM uploaded_items WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): UploadedItemEntity?
}
