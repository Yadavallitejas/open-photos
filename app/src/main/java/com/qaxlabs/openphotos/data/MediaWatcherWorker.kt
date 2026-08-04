package com.qaxlabs.openphotos.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager [CoroutineWorker] that scans MediaStore for new photos/videos
 * and feeds them into [UploadRepository]'s sequential queue.
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This worker does NOT call TDLib directly. It delegates to [UploadRepository],
 * which is the sole gatekeeper for TDLib upload interactions.
 *
 * Execution contract:
 *  - Returns [Result.success] immediately after enqueuing — it does NOT block
 *    until uploads finish. The existing processor coroutine in [UploadRepository]
 *    handles completion asynchronously.
 *  - Returns [Result.success] (no retry) on early-exit conditions (feature off,
 *    not authenticated) — nothing to retry if we deliberately skipped.
 *  - WorkManager will reschedule the next periodic run automatically.
 *
 * Deduplication strategy (two-layer):
 *  1. DATE_ADDED watermark  — only MediaStore items added after the last scan.
 *  2. Room URI check        — items already present in Room are skipped even if
 *     the watermark missed them (e.g., on the very first run, watermark = 0).
 *
 * Size pre-flight (FR-UPLOAD-5):
 *  Files ≥ 2 GB are excluded here; [UploadRepository.enqueue] would flag them
 *  as [UploadState.Oversized] anyway, but keeping them out avoids polluting the
 *  queue UI with red badges the user can't act on in the background.
 */
@HiltWorker
class MediaWatcherWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupPreferences: BackupPreferences,
    private val authRepository: TelegramAuthRepository,
    private val mediaRepository: MediaRepository,
    private val uploadRepository: UploadRepository,
    private val dao: UploadedItemDao,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "MediaWatcherPeriodicWork"
        private const val TAG = "MediaWatcherWorker"
        /** 2 GB pre-flight ceiling — mirrors UploadRepository's SIZE_LIMIT_BYTES. */
        private const val SIZE_LIMIT_BYTES = 2L * 1_024 * 1_024 * 1_024
    }

    override suspend fun doWork(): Result {
        // ── Guard 1: feature toggle ───────────────────────────────────────────
        if (!backupPreferences.isAutoBackupEnabled()) {
            Log.d(TAG, "Auto-backup is disabled; skipping scan.")
            return Result.success()
        }

        // ── Guard 2: must be authenticated (TDLib session required for uploads) ─
        val currentAuth = authRepository.authState.value
        if (currentAuth !is AuthState.Authenticated) {
            Log.d(TAG, "Not authenticated (state=$currentAuth); skipping scan.")
            return Result.success()
        }

        Log.d(TAG, "Starting media scan…")

        val lastSeenTs = backupPreferences.getLastSeenTimestamp()

        // ── Query MediaStore for items newer than the watermark ───────────────
        val allMedia = mediaRepository.queryAll()
        val candidates = allMedia.filter { it.dateAdded > lastSeenTs }

        if (candidates.isEmpty()) {
            Log.d(TAG, "No new media since watermark=$lastSeenTs")
            return Result.success()
        }

        Log.d(TAG, "Found ${candidates.size} candidate(s) newer than watermark=$lastSeenTs")

        // ── Dedup via Room + size pre-flight ──────────────────────────────────
        val toUpload = candidates.filter { item ->
            when {
                item.sizeBytes >= SIZE_LIMIT_BYTES -> {
                    Log.w(TAG, "Skipping oversized file: ${item.displayName} (${item.sizeBytes}B)")
                    false
                }
                dao.existsByLocalUri(item.uri.toString()) -> {
                    Log.d(TAG, "Already backed up, skipping: ${item.displayName}")
                    false
                }
                else -> true
            }
        }

        if (toUpload.isNotEmpty()) {
            Log.i(TAG, "Enqueuing ${toUpload.size} item(s) for upload.")
            uploadRepository.enqueue(toUpload)
        } else {
            Log.d(TAG, "All candidates already backed up or oversized; nothing to enqueue.")
        }

        // ── Advance watermark to the max DATE_ADDED of all candidates scanned ─
        val maxTs = candidates.maxOf { it.dateAdded }
        backupPreferences.setLastSeenTimestamp(maxTs)
        Log.d(TAG, "Watermark advanced to $maxTs")

        return Result.success()
    }
}
