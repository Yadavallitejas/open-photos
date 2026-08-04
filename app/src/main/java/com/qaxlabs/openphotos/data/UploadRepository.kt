package com.qaxlabs.openphotos.data

import android.content.Context
import android.util.Log
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import com.qaxlabs.openphotos.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ── Flood-wait state (FR-BACKUP-STATUS-2) ─────────────────────────────────────

/**
 * Exposes whether the upload queue is currently paused due to a Telegram
 * flood-wait (FLOOD_WAIT_X error / HTTP 429).
 *
 * [None]    — queue is running normally.
 * [Waiting] — paused; [resumesAt] is the epoch-millis when the delay expires.
 *             The UI can compute a human-readable countdown from this value.
 */
sealed class FloodWaitState {
    object None : FloodWaitState()
    data class Waiting(val resumesAt: Long) : FloodWaitState()
}

/** Free-account file size ceiling (2 GB). Files at or above this are flagged. */
private const val SIZE_LIMIT_BYTES: Long = 2L * 1_024 * 1_024 * 1_024

/** Auto-retry budget for Telegram flood-waits (FR-UPLOAD-2). */
private const val MAX_RETRIES = 3

/** Per-upload timeout: 30 min should be enough for a 2 GB file on any connection. */
private const val UPLOAD_TIMEOUT_MS = 30L * 60 * 1_000

/**
 * Sequential upload queue that backs up device media to Telegram Saved Messages.
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This is the ONLY class besides [TelegramAuthRepository] permitted to call
 * [TelegramClient] directly. No ViewModel or UI class may touch TDLib.
 *
 * Queue semantics (FR-UPLOAD-2):
 *   Items are processed one at a time. The processor coroutine blocks on each
 *   upload's completion before dequeuing the next item — there is no parallelism
 *   at the TDLib layer.
 *
 * State visibility (FR-UPLOAD-3):
 *   [queue] is a [StateFlow] that every subscriber observes in real-time.
 *   Each state transition (Queued → Uploading → Done / Failed) emits a new
 *   immutable snapshot.
 */
@Singleton
class UploadRepository @Inject constructor(
    private val client: TelegramClient,
    private val dao: UploadedItemDao,
    private val indexRepository: IndexRepository,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _queue = MutableStateFlow<List<UploadQueueItem>>(emptyList())
    val queue: StateFlow<List<UploadQueueItem>> = _queue.asStateFlow()

    /** FR-BACKUP-STATUS-2: observed by UploadViewModel → UploadScreen banner. */
    private val _floodWaitState = MutableStateFlow<FloodWaitState>(FloodWaitState.None)
    val floodWaitState: StateFlow<FloodWaitState> = _floodWaitState.asStateFlow()

    /**
     * CONFLATED channel: multiple enqueue calls while the processor is busy
     * collapse to a single pending "wake-up" signal, preventing redundant iterations.
     */
    private val workSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Lazily cached Saved Messages chat ID. Populated on the first upload and
     * reused for all subsequent items. Avoids a GetMe + CreatePrivateChat round-
     * trip per file.
     */
    @Volatile private var savedMessagesChatId: Long = 0L

    init {
        // Single processor coroutine — lives for the entire app lifetime.
        scope.launch {
            for (signal in workSignal) {
                processAllQueued()
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Adds [items] to the queue.
     *
     * FR-UPLOAD-5: Any item ≥ 2 GB is immediately tagged [UploadState.Oversized]
     * and will never enter the processor loop. This surfaces the problem to the
     * user before a multi-gigabyte upload starts — not after it fails halfway.
     */
    fun enqueue(items: List<MediaItem>) {
        val newItems = items.map { media ->
            UploadQueueItem(
                mediaItem = media,
                state = if (media.sizeBytes >= SIZE_LIMIT_BYTES)
                    UploadState.Oversized
                else
                    UploadState.Queued,
            )
        }
        _queue.update { current -> current + newItems }
        workSignal.trySend(Unit)
    }

    /** Clears the queue (e.g. after a completed session or explicit user action). */
    fun clearQueue() {
        _queue.value = emptyList()
    }

    /**
     * Resets a [UploadState.Failed] queue item back to [UploadState.Queued]
     * and triggers the upload processor (FR-UPLOAD-4).
     */
    fun retryItem(itemId: String) {
        _queue.update { current ->
            current.map { item ->
                if (item.id == itemId && item.state is UploadState.Failed) {
                    item.copy(state = UploadState.Queued)
                } else item
            }
        }
        workSignal.trySend(Unit)
    }

    // ── Processor ─────────────────────────────────────────────────────────────

    /**
     * Processes every [UploadState.Queued] item sequentially.
     * Called from the processor coroutine each time [workSignal] fires.
     */
    private suspend fun processAllQueued() {
        while (true) {
            val next = _queue.value.firstOrNull { it.state is UploadState.Queued }
                ?: return
            uploadItem(next)
        }
    }

    // ── Upload logic ──────────────────────────────────────────────────────────

    private suspend fun uploadItem(item: UploadQueueItem) {
        var retriesLeft = MAX_RETRIES
        while (true) {
            setItemState(item.id, UploadState.Uploading(0L, item.mediaItem.sizeBytes))
            try {
                doUpload(item)
                return          // success → exit the retry loop
            } catch (e: TelegramException) {
                if (e.code == 429 && retriesLeft > 0) {
                    // Telegram flood-wait: parse the retry-after delay (FR-UPLOAD-2).
                    // Surface the pause to the UI (FR-BACKUP-STATUS-2).
                    val waitSeconds = parseRetryAfter(e.message ?: "")
                    _floodWaitState.value = FloodWaitState.Waiting(
                        resumesAt = System.currentTimeMillis() + waitSeconds * 1_000L
                    )
                    retriesLeft--
                    kotlinx.coroutines.delay(waitSeconds * 1_000L)
                    _floodWaitState.value = FloodWaitState.None   // delay expired → clear
                    // loop → retry
                } else {
                    setItemState(item.id, UploadState.Failed(
                        reason      = e.message ?: "Telegram error ${e.code}",
                        retriesLeft = retriesLeft,
                    ))
                    return
                }
            } catch (e: Exception) {
                setItemState(item.id, UploadState.Failed(e.message ?: "Unexpected error"))
                return
            }
        }
    }

    /**
     * Performs a single upload attempt.
     * Throws [TelegramException] or [Exception] on any failure.
     */
    private suspend fun doUpload(item: UploadQueueItem) {
        // 1. Resolve the absolute path that TDLib needs for InputFileLocal.
        val absolutePath = item.mediaItem.absolutePath
            ?: copyToCache(item.mediaItem)

        // 1b. Checksum duplicate check (FR-UPLOAD / DRD edge cases):
        //     If a file with the exact same SHA-256 already exists in Room, skip sending to Telegram.
        val sha256 = computeSha256(absolutePath)
        val existing = dao.findBySha256(sha256)
        if (existing != null) {
            Log.i("UploadRepository", "Skipping duplicate upload for ${item.mediaItem.displayName} (matching sha256=$sha256)")
            setItemState(item.id, UploadState.Done(existing.telegramMessageId))
            return
        }

        // 2. Lazily resolve Saved Messages chat ID.
        val chatId = getSavedMessagesChatId()

        // 3. Build the Document message — FR-UPLOAD-1: always Document, never Photo/Video.
        //    disableContentTypeDetection = true → TDLib never re-classifies as Photo/Video.
        val inputContent = TdApi.InputMessageDocument(
            TdApi.InputFileLocal(absolutePath),
            null,   // thumbnail — let Telegram generate it server-side
            true,   // disableContentTypeDetection → keeps it as a raw Document
            null,   // caption
        )

        // 4. Send the message. TDLib returns a temporary message with a negative ID.
        //    Use field assignment to avoid positional constructor issues across TDLib versions.
        val sendReq = TdApi.SendMessage().also { m ->
            m.chatId = chatId
            m.inputMessageContent = inputContent
            // replyTo, messageThreadId, options, replyMarkup: leave as default (null/0)
        }
        val tempMessage = client.send(sendReq) as TdApi.Message


        val tempMessageId = tempMessage.id         // negative (local-only) ID
        val fileId = extractFileId(tempMessage)    // TDLib file ID for progress tracking

        // 5. Track per-byte upload progress via UpdateFile (FR-UPLOAD-3).
        //    Runs concurrently with the completion wait below.
        val progressJob = scope.launch {
            client.updates
                .filterIsInstance<TdApi.UpdateFile>()
                .collect { upd ->
                    if (upd.file.id == fileId) {
                        val uploaded = upd.file.remote.uploadedSize
                        val total    = if (upd.file.size > 0L) upd.file.size
                                       else item.mediaItem.sizeBytes
                        setItemState(item.id, UploadState.Uploading(uploaded, total))
                    }
                }
        }

        // 6. Wait for Telegram to acknowledge the message as sent (upload complete).
        val successUpdate = try {
            withTimeout(UPLOAD_TIMEOUT_MS) {
                client.updates
                    .filterIsInstance<TdApi.UpdateMessageSendSucceeded>()
                    .first { it.oldMessageId == tempMessageId }
            }
        } finally {
            progressJob.cancel()    // stop progress collection regardless of outcome
        }

        val realMessageId = successUpdate.message.id

        // 7. Reuse SHA-256 computed above for the index (FR-INDEX-3 checksum requirement).
        //    (already computed at start of doUpload)

        // 8. Persist to Room — includes the new indexId and sha256 for FR-INDEX.
        dao.insert(
            UploadedItemEntity(
                indexId           = item.id,   // queue item UUID doubles as stable index ID
                localUri          = item.mediaItem.uri.toString(),
                displayName       = item.mediaItem.displayName,
                sizeBytes         = item.mediaItem.sizeBytes,
                mimeType          = item.mediaItem.mimeType,
                sha256            = sha256,
                takenAt           = item.mediaItem.dateAdded * 1_000L,  // seconds → millis
                telegramChatId    = chatId,
                telegramMessageId = realMessageId,
                uploadedAt        = System.currentTimeMillis(),
            )
        )

        // 9. Push updated index to Saved Messages (FR-INDEX-1).
        //    Runs on the same sequential coroutine — no concurrency risk.
        indexRepository.pushIndex()

        setItemState(item.id, UploadState.Done(realMessageId))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun getSavedMessagesChatId(): Long {
        if (savedMessagesChatId != 0L) return savedMessagesChatId
        val me   = client.send(TdApi.GetMe()) as TdApi.User
        val chat = client.send(TdApi.CreatePrivateChat(me.id, false)) as TdApi.Chat
        savedMessagesChatId = chat.id
        return chat.id
    }

    /** Extracts the TDLib file ID from a just-sent Document message. */
    private fun extractFileId(message: TdApi.Message): Int {
        val content = message.content
        return if (content is TdApi.MessageDocument) content.document.document.id else 0
    }

    /**
     * Copies a media item to the app's cache directory when [absolutePath] is null
     * (e.g., on virtual storage providers that hide the real path).
     * The cache file is deleted after upload in a real implementation; for v1 we
     * leave it to the OS cache-eviction policy.
     */
    private fun copyToCache(item: MediaItem): String {
        val cacheFile = File(context.cacheDir, "upload_${item.id}_${item.displayName}")
        if (!cacheFile.exists()) {
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot open stream for ${item.displayName}")
        }
        return cacheFile.absolutePath
    }

    /** Parses the "retry after N" value from a Telegram 429 error message. */
    private fun parseRetryAfter(message: String): Long {
        val match = Regex("retry after (\\d+)").find(message)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 60L
    }

    private fun setItemState(id: String, state: UploadState) {
        _queue.update { current ->
            current.map { if (it.id == id) it.copy(state = state) else it }
        }
    }

    /**
     * Streams the file at [path] through SHA-256 in 64 KB chunks.
     * Returns the checksum in "sha256:<hex>" format (FR-INDEX-3).
     */
    private suspend fun computeSha256(path: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536)
        File(path).inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }
}
