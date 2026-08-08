package com.qaxlabs.openphotos.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import com.qaxlabs.openphotos.data.MediaItem
import java.io.File
import java.io.FileOutputStream

/**
 * Thumbnail generation result containing the compressed JPEG file and its dimensions.
 */
data class GeneratedThumbnail(
    val file: File,
    val width: Int,
    val height: Int,
)

/**
 * Helper to generate small JPEG thumbnails (max 320px dimension) for photos and videos (FR-UPLOAD-6).
 */
object ThumbnailGenerator {
    private const val TAG = "ThumbnailGenerator"
    private const val MAX_THUMB_SIZE = 320

    fun generateThumbnail(context: Context, item: MediaItem): GeneratedThumbnail? {
        return try {
            val bitmap = if (item.isVideo) {
                generateVideoThumbnail(context, item)
            } else {
                generateImageThumbnail(context, item)
            } ?: return null

            val width = bitmap.width
            val height = bitmap.height

            val thumbFile = File(context.cacheDir, "thumb_gen_${item.id}.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()

            GeneratedThumbnail(thumbFile, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate thumbnail for ${item.displayName}", e)
            null
        }
    }

    private fun generateImageThumbnail(context: Context, item: MediaItem): Bitmap? {
        val uri = item.uri
        val path = item.absolutePath

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (path != null && File(path).exists()) {
            BitmapFactory.decodeFile(path, options)
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }

        val origWidth = options.outWidth
        val origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        var sampleSize = 1
        while (origWidth / sampleSize > MAX_THUMB_SIZE || origHeight / sampleSize > MAX_THUMB_SIZE) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val scaledBitmap = if (path != null && File(path).exists()) {
            BitmapFactory.decodeFile(path, decodeOptions)
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } ?: return null

        return scaleBitmapToMax(scaledBitmap, MAX_THUMB_SIZE)
    }

    private fun generateVideoThumbnail(context: Context, item: MediaItem): Bitmap? {
        val path = item.absolutePath
        val uri = item.uri

        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(MAX_THUMB_SIZE, MAX_THUMB_SIZE), null)
            } catch (_: Exception) {
                fetchVideoFrameRetriever(context, uri, path)
            }
        } else {
            fetchVideoFrameRetriever(context, uri, path)
        }

        return bitmap?.let { scaleBitmapToMax(it, MAX_THUMB_SIZE) }
    }

    private fun fetchVideoFrameRetriever(context: Context, uri: Uri, path: String?): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (path != null && File(path).exists()) {
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(context, uri)
            }
            retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun scaleBitmapToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap

        val ratio = w.toFloat() / h.toFloat()
        val targetW: Int
        val targetH: Int
        if (w > h) {
            targetW = maxDim
            targetH = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxDim
            targetW = (maxDim * ratio).toInt().coerceAtLeast(1)
        }

        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled != bitmap) {
            bitmap.recycle()
        }
        return scaled
    }
}
