package com.qaxlabs.openphotos.data

import android.content.Context
import android.util.Log
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages downloading and local disk caching of lightweight photo/video thumbnail previews (FR-GALLERY-2).
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * Interacts with [TelegramClient] to fetch Document thumbnails from Telegram servers.
 * Cached thumbnail files live in `<cacheDir>/vault_thumbnails/<indexId>.jpg`.
 */
@Singleton
class ThumbnailRepository @Inject constructor(
    private val client: TelegramClient,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ThumbnailRepository"
        private const val DOWNLOAD_TIMEOUT_MS = 20_000L
    }

    private val fetchMutex = Mutex()
    private val thumbsDir = File(context.cacheDir, "vault_thumbnails").also { it.mkdirs() }

    /**
     * Synchronously/suspendingly resolves the local thumbnail file path for [entity].
     *
     * 1. Returns cached `<cacheDir>/vault_thumbnails/<indexId>.jpg` if already downloaded.
     * 2. Checks if [entity.localUri] is accessible on the current device (e.g. before wipe).
     * 3. Fetches the Document's thumbnail from Telegram via TDLib and saves it locally.
     */
    suspend fun getOrFetchThumbnailFile(entity: UploadedItemEntity): File? = withContext(Dispatchers.IO) {
        val cachedFile = File(thumbsDir, "${entity.indexId}.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return@withContext cachedFile
        }

        // Check if localUri is an existing file on disk (e.g., original device prior to data wipe)
        val localFilePath = entity.localUri.let { uriStr ->
            if (uriStr.startsWith("file://")) uriStr.removePrefix("file://")
            else uriStr
        }
        val localFile = File(localFilePath)
        if (localFile.exists() && localFile.length() > 0) {
            return@withContext localFile
        }

        // Remote fetch via TDLib
        fetchMutex.withLock {
            // Re-check after acquiring lock
            if (cachedFile.exists() && cachedFile.length() > 0) {
                return@withLock cachedFile
            }

            try {
                // Fetch Telegram Document message
                val message = runCatching {
                    client.send(TdApi.GetMessage(entity.telegramChatId, entity.telegramMessageId)) as TdApi.Message
                }.getOrNull() ?: return@withLock null

                val content = message.content as? TdApi.MessageDocument ?: return@withLock null
                val doc = content.document
                val thumbnail = doc.thumbnail ?: return@withLock null
                val file = thumbnail.file

                // Download thumbnail file if not already completed
                val downloadedPath = if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) {
                    file.local.path
                } else {
                    client.send(TdApi.DownloadFile(file.id, 32, 0, 0, false))
                    val completed = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                        client.updates
                            .filterIsInstance<TdApi.UpdateFile>()
                            .first { it.file.id == file.id && it.file.local.isDownloadingCompleted }
                    }
                    completed?.file?.local?.path
                }

                if (downloadedPath != null) {
                    val srcFile = File(downloadedPath)
                    if (srcFile.exists() && srcFile.length() > 0) {
                        srcFile.copyTo(cachedFile, overwrite = true)
                        Log.d(TAG, "Cached remote thumbnail for indexId=${entity.indexId}")
                        return@withLock cachedFile
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download thumbnail for ${entity.indexId}", e)
            }

            return@withLock null
        }
    }
}
