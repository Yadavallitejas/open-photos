package com.qaxlabs.openphotos.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** State of full-resolution file retrieval for the detail viewer. */
sealed interface ViewerFileState {
    /** No fetch started for this item. */
    data object Idle : ViewerFileState

    /** TDLib download in progress: 0.0 – 1.0 progress. */
    data class Loading(val progress: Float = 0f) : ViewerFileState

    /** Full file is ready at [localPath]. */
    data class Ready(val localPath: String) : ViewerFileState

    /** Fetch failed. */
    data class Error(val message: String) : ViewerFileState
}

/**
 * Manages full-resolution Telegram file downloads for the vault detail viewer
 * (FR-GALLERY-3 / FR-GALLERY-4).
 *
 * CACHE CONTRACT:
 * Files are cached in `<cacheDir>/vault_fullres/<indexId>.<ext>`.
 * This directory is NEVER persisted to MediaStore automatically — the user must
 * explicitly invoke [saveToMediaStore] (FR-GALLERY-5 / "Download" action).
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * Calls [TelegramClient.send] only. Interacts with [MediaStore] only for the
 * explicit download action.
 */
@Singleton
class ViewerRepository @Inject constructor(
    private val client: TelegramClient,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ViewerRepository"
        private const val DOWNLOAD_TIMEOUT_MS = 120_000L // 2 min for large files
    }

    private val fullresDir = File(context.cacheDir, "vault_fullres").also { it.mkdirs() }

    // Per-indexId state exposed to the UI.
    private val _stateMap = MutableStateFlow<Map<String, ViewerFileState>>(emptyMap())
    val stateMap: StateFlow<Map<String, ViewerFileState>> = _stateMap.asStateFlow()

    fun stateFor(indexId: String): ViewerFileState =
        _stateMap.value[indexId] ?: ViewerFileState.Idle

    private fun updateState(indexId: String, state: ViewerFileState) {
        _stateMap.value = _stateMap.value + (indexId to state)
    }

    /**
     * Fetches the full-resolution file for [entity]. Idempotent — returns
     * immediately if already [ViewerFileState.Ready] or already in progress.
     */
    suspend fun fetchFullResolution(entity: UploadedItemEntity) = withContext(Dispatchers.IO) {
        val indexId = entity.indexId

        // Already done?
        val current = stateFor(indexId)
        if (current is ViewerFileState.Ready) return@withContext
        if (current is ViewerFileState.Loading) return@withContext

        // Local file still on device (e.g. not wiped)?
        val localUriStr = entity.localUri
        val localFile = when {
            localUriStr.startsWith("file://") -> File(localUriStr.removePrefix("file://"))
            localUriStr.startsWith("/") -> File(localUriStr)
            else -> null
        }
        if (localFile != null && localFile.exists() && localFile.length() > 0) {
            updateState(indexId, ViewerFileState.Ready(localFile.absolutePath))
            return@withContext
        }

        // Check cache
        val ext = entity.mimeType.substringAfterLast('/').ifBlank { "bin" }
        val cacheFile = File(fullresDir, "${indexId}.${ext}")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            updateState(indexId, ViewerFileState.Ready(cacheFile.absolutePath))
            return@withContext
        }

        updateState(indexId, ViewerFileState.Loading(0f))

        try {
            // Fetch message to get the Document file ID
            val message = runCatching {
                client.send(TdApi.GetMessage(entity.telegramChatId, entity.telegramMessageId)) as TdApi.Message
            }.getOrNull()

            if (message == null) {
                updateState(indexId, ViewerFileState.Error("Could not retrieve message from Telegram"))
                return@withContext
            }

            val content = message.content as? TdApi.MessageDocument
            if (content == null) {
                updateState(indexId, ViewerFileState.Error("Unexpected message content type"))
                return@withContext
            }

            val file = content.document.document
            val fileId = file.id

            // Start download
            if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) {
                // Already downloaded by TDLib
                val srcFile = File(file.local.path)
                if (srcFile.exists()) {
                    srcFile.copyTo(cacheFile, overwrite = true)
                    updateState(indexId, ViewerFileState.Ready(cacheFile.absolutePath))
                    return@withContext
                }
            }

            client.send(TdApi.DownloadFile(fileId, 1, 0, 0, false))

            val completed = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                client.updates
                    .filterIsInstance<TdApi.UpdateFile>()
                    .first { update ->
                        val f = update.file
                        if (f.id == fileId) {
                            // Emit progress updates
                            if (!f.local.isDownloadingCompleted && f.expectedSize > 0) {
                                val progress =
                                    f.local.downloadedSize.toFloat() / f.expectedSize.toFloat()
                                updateState(indexId, ViewerFileState.Loading(progress.coerceIn(0f, 1f)))
                            }
                            f.local.isDownloadingCompleted
                        } else false
                    }
            }

            val downloadedPath = completed?.file?.local?.path
            if (downloadedPath.isNullOrEmpty()) {
                updateState(indexId, ViewerFileState.Error("Download timed out or failed"))
                return@withContext
            }

            val srcFile = File(downloadedPath)
            if (srcFile.exists() && srcFile.length() > 0) {
                srcFile.copyTo(cacheFile, overwrite = true)
                updateState(indexId, ViewerFileState.Ready(cacheFile.absolutePath))
                Log.d(TAG, "Full-res cached: $cacheFile")
            } else {
                updateState(indexId, ViewerFileState.Error("Downloaded file is missing or empty"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchFullResolution failed for $indexId", e)
            updateState(indexId, ViewerFileState.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Saves the already-cached full-resolution file to the device gallery via
     * MediaStore. This is the explicit "Download" action (FR-GALLERY-5).
     *
     * Returns the [Uri] of the newly inserted MediaStore entry, or null on failure.
     */
    suspend fun saveToMediaStore(
        entity: UploadedItemEntity,
        localPath: String,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(localPath)
            if (!srcFile.exists()) return@withContext null

            val mimeType = entity.mimeType
            val displayName = entity.displayName

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = if (mimeType.startsWith("video/")) {
                        "${Environment.DIRECTORY_MOVIES}/OpenPhotos"
                    } else {
                        "${Environment.DIRECTORY_PICTURES}/OpenPhotos"
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (mimeType.startsWith("video/")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, contentValues) ?: return@withContext null

            resolver.openOutputStream(uri)?.use { out ->
                srcFile.inputStream().use { it.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }

            Log.d(TAG, "Saved to MediaStore: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "saveToMediaStore failed for ${entity.indexId}", e)
            null
        }
    }
}
