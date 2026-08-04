package com.qaxlabs.openphotos.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries the device's [MediaStore] for photos and videos, newest first.
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * Only [MediaViewModel] calls this. Never call it from a composable directly.
 *
 * Permission requirement (FR-MEDIA-1):
 *   API 33+  → READ_MEDIA_IMAGES + READ_MEDIA_VIDEO must be granted
 *   API 26–32 → READ_EXTERNAL_STORAGE must be granted
 * Callers must ensure permissions are granted before calling [queryAll].
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val SORT_ORDER = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        @Suppress("DEPRECATION")   // DATA column deprecated since API 29 but still readable
        private val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATA,       // absolute filesystem path for TDLib
        )
    }

    /**
     * Returns all images and videos from external storage, merged and sorted
     * newest-first. Runs on [Dispatchers.IO].
     */
    suspend fun queryAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        buildList {
            addAll(queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            addAll(queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
            sortByDescending { it.dateAdded }
        }
    }

    @Suppress("DEPRECATION")
    private fun queryCollection(collectionUri: Uri): List<MediaItem> {
        val results = mutableListOf<MediaItem>()

        // On API 29+ we could use MediaStore.setRequireOriginal() and
        // setIncludePending(), but for v1 we keep it simple.
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.setRequireOriginal(collectionUri)
        } else {
            collectionUri
        }

        context.contentResolver.query(
            queryUri,
            PROJECTION,
            null,
            null,
            SORT_ORDER,
        )?.use { cursor ->
            val idCol          = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol        = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol        = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol        = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol        = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val dataCol        = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val id           = cursor.getLong(idCol)
                val displayName  = cursor.getString(nameCol) ?: "Unknown"
                val sizeBytes    = cursor.getLong(sizeCol)
                val mimeType     = cursor.getString(mimeCol) ?: "application/octet-stream"
                val dateAdded    = cursor.getLong(dateCol)
                val absolutePath = if (dataCol >= 0) cursor.getString(dataCol) else null

                val contentUri = ContentUris.withAppendedId(collectionUri, id)

                results += MediaItem(
                    id           = id,
                    uri          = contentUri,
                    displayName  = displayName,
                    sizeBytes    = sizeBytes,
                    mimeType     = mimeType,
                    dateAdded    = dateAdded,
                    absolutePath = absolutePath,
                )
            }
        }

        return results
    }
}
