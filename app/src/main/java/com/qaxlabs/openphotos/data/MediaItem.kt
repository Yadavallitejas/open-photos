package com.qaxlabs.openphotos.data

import android.net.Uri

/**
 * A single photo or video item read from the device's MediaStore.
 *
 * [absolutePath] is populated from [MediaStore.MediaColumns.DATA].
 * This column is deprecated since API 29 but remains readable for all
 * MediaStore-managed media files. TDLib's [InputFileLocal] requires an
 * actual filesystem path, not a content URI, so we capture it here.
 * If null (e.g. virtual storage providers), [MediaRepository] returns null
 * and [UploadRepository] will copy the file to the app's cache dir as a
 * fallback before uploading.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    /** Epoch seconds — used for "newest first" sort. */
    val dateAdded: Long,
    /** Absolute file-system path; may be null on some storage providers. */
    val absolutePath: String?,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
}
