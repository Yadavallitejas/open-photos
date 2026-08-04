package com.qaxlabs.openphotos.data

import android.content.Context
import android.util.Log
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import com.qaxlabs.openphotos.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.drinkless.tdlib.TdApi
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// ── Sync state ────────────────────────────────────────────────────────────────

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Done(val itemCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

// ── IndexRepository ────────────────────────────────────────────────────────────

/**
 * Manages the serverless vault index stored as a JSON Document in the user's
 * own Telegram Saved Messages (FR-INDEX).
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This is the ONLY class besides [TelegramAuthRepository] and [UploadRepository]
 * permitted to call [TelegramClient] directly. No ViewModel touches TDLib.
 *
 * ### "Fixed message" strategy (FR-INDEX-1)
 * The index JSON is written to a Document named [VaultIndex.INDEX_FILENAME].
 *  - First write  → `SendMessage` → message ID cached in [SecureStore].
 *  - Subsequent   → `EditMessageMedia` → same message ID, document replaced.
 *  - After reinstall, [SecureStore] is cleared → [findIndexMessage] scans recent
 *    Saved Messages history for the file name as a fallback.
 *
 * ### Remote-wins reconciliation (FR-INDEX-4)
 * [syncFromRemote] wipes Room then re-populates from the remote index,
 * giving the Telegram-stored copy unconditional authority.
 */
@Singleton
class IndexRepository @Inject constructor(
    private val client: TelegramClient,
    private val dao: UploadedItemDao,
    private val secureStore: SecureStore,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "IndexRepository"
        private const val PUSH_TIMEOUT_MS = 60_000L
        private const val DOWNLOAD_TIMEOUT_MS = 30_000L
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /** Prevents concurrent push operations from racing. */
    private val pushMutex = Mutex()

    /** Lazily cached Saved Messages chat ID. */
    @Volatile private var savedMessagesChatId: Long = 0L

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Pulls the remote index from Saved Messages and reconciles it into Room.
     *
     * FR-INDEX-2: Called on every successful login (including on a fresh install
     * against an account that already has uploads).
     * FR-INDEX-4: Remote wins — local Room is cleared and repopulated.
     *
     * Non-fatal: if the remote index is unavailable (network, first-time account),
     * the error is surfaced via [syncState] but the app continues normally.
     */
    suspend fun syncFromRemote() {
        _syncState.value = SyncState.Syncing
        try {
            val chatId = getSavedMessagesChatId()
            val indexMessage = findIndexMessage(chatId)

            if (indexMessage != null) {
                val jsonText = downloadDocumentText(indexMessage)
                val index = Json.decodeFromString<VaultIndex>(jsonText)

                // FR-INDEX-4: remote wins — clear local, insert all remote items
                dao.clearAll()
                dao.insertAll(index.files.map { entry -> entry.toEntity(chatId) })

                // Re-cache the message ID (covers the post-reinstall recovery path)
                secureStore.setIndexMessageId(indexMessage.id)

                Log.i(TAG, "Synced ${index.files.size} items from remote index")
                _syncState.value = SyncState.Done(index.files.size)
            } else {
                // No index found: fresh account or user manually deleted it
                Log.i(TAG, "No remote index found — fresh account")
                _syncState.value = SyncState.Done(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncFromRemote failed", e)
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }

    /**
     * Builds the full index from Room and writes/edits it in Saved Messages.
     *
     * FR-INDEX-1: Called after every successful upload or deletion.
     * Serialised via [pushMutex] so concurrent calls (e.g. rapid uploads) don't
     * race — only the last caller's snapshot wins.
     *
     * Non-fatal: push failures are logged but do not fail the upload itself.
     */
    suspend fun pushIndex() {
        pushMutex.withLock {
            try {
                val chatId = getSavedMessagesChatId()

                // Build the JSON from the current Room contents
                val entities = dao.getAllOnce()
                val index = VaultIndex(
                    files = entities.map { entity -> entity.toIndexEntry() },
                )
                val jsonText = Json.encodeToString(index)

                // Write to a temp file that TDLib can upload
                val tmpFile = File(context.cacheDir, VaultIndex.INDEX_FILENAME)
                tmpFile.writeText(jsonText)

                val inputDoc = TdApi.InputMessageDocument(
                    TdApi.InputFileLocal(tmpFile.absolutePath),
                    null,
                    true,   // disableContentTypeDetection — keep as Document
                    TdApi.FormattedText(VaultIndex.INDEX_CAPTION, emptyArray()),
                )

                val cachedMsgId = secureStore.getIndexMessageId()
                if (cachedMsgId != null) {
                    editIndexMessage(chatId, cachedMsgId, inputDoc)
                } else {
                    sendIndexMessage(chatId, inputDoc)
                }
            } catch (e: Exception) {
                // Index push failure is non-fatal: the upload already succeeded
                // and Room is already updated. The index will be consistent on
                // the next push or after a manual sync.
                Log.e(TAG, "pushIndex failed (non-fatal)", e)
            }
        }
    }

    // ── Private — Telegram operations ─────────────────────────────────────────

    /**
     * Edits the existing index message in-place.
     * If the edit fails (e.g. the message was manually deleted by the user),
     * falls back to sending a fresh message.
     */
    private suspend fun editIndexMessage(
        chatId: Long,
        messageId: Long,
        inputDoc: TdApi.InputMessageDocument,
    ) {
        try {
            val editReq = TdApi.EditMessageMedia().also { m ->
                m.chatId               = chatId
                m.messageId            = messageId
                m.inputMessageContent  = inputDoc
            }
            client.send(editReq)   // throws TelegramException on failure
            Log.d(TAG, "Edited index message $messageId")
        } catch (e: TelegramException) {
            // Message gone — clear the cached ID and send a new one
            Log.w(TAG, "Edit failed (${e.message}); sending new index message")
            secureStore.clearIndexMessageId()
            sendIndexMessage(chatId, inputDoc)
        }
    }

    /**
     * Sends a new index Document message and caches its permanent message ID.
     */
    private suspend fun sendIndexMessage(chatId: Long, inputDoc: TdApi.InputMessageDocument) {
        val sendReq = TdApi.SendMessage().also { m ->
            m.chatId              = chatId
            m.inputMessageContent = inputDoc
        }
        val tempMessage = client.send(sendReq) as TdApi.Message

        // Wait for the send to be acknowledged so we get the permanent message ID
        val success = withTimeout(PUSH_TIMEOUT_MS) {
            client.updates
                .filterIsInstance<TdApi.UpdateMessageSendSucceeded>()
                .first { it.oldMessageId == tempMessage.id }
        }
        val permanentId = success.message.id
        secureStore.setIndexMessageId(permanentId)
        Log.i(TAG, "Sent new index message, permanent ID = $permanentId")
    }

    /**
     * Finds the index Document message in Saved Messages.
     *
     * Strategy (ordered by cost):
     * 1. Use cached message ID from [SecureStore] (O(1), no network).
     * 2. Scan up to 200 recent messages in Saved Messages for the index file name.
     * 3. Return null if not found (first install / user manually deleted it).
     */
    private suspend fun findIndexMessage(chatId: Long): TdApi.Message? {
        // 1. Try cached ID first
        val cachedId = secureStore.getIndexMessageId()
        if (cachedId != null) {
            val message = runCatching {
                client.send(TdApi.GetMessage(chatId, cachedId)) as TdApi.Message
            }.getOrNull()
            if (message != null && message.content is TdApi.MessageDocument) {
                return message
            }
            // Message gone — clear stale cache and fall through to scan
            secureStore.clearIndexMessageId()
        }

        // 2. Scan recent history for the index file
        return scanHistoryForIndex(chatId)
    }

    /**
     * Walks backward through Saved Messages history looking for the index Document.
     * Scans at most 200 messages (2 × 100-message pages) before giving up.
     */
    private suspend fun scanHistoryForIndex(chatId: Long): TdApi.Message? {
        var fromMessageId = 0L  // 0 = start from the newest message
        repeat(2) {
            val historyResult = runCatching {
                val req = TdApi.GetChatHistory().also { r ->
                    r.chatId        = chatId
                    r.fromMessageId = fromMessageId
                    r.offset        = 0
                    r.limit         = 100
                    r.onlyLocal     = false
                }
                client.send(req) as TdApi.Messages
            }.getOrNull() ?: return null

            for (msg in historyResult.messages) {
                val content = msg.content
                if (content is TdApi.MessageDocument &&
                    content.document.fileName == VaultIndex.INDEX_FILENAME) {
                    Log.i(TAG, "Found index message via scan: ${msg.id}")
                    return msg
                }
            }

            if (historyResult.messages.isEmpty()) return null
            fromMessageId = historyResult.messages.last().id
        }
        return null
    }

    /**
     * Downloads the text content of a Document message.
     *
     * Uses TDLib's synchronous [TdApi.DownloadFile] (priority 32) which blocks
     * the coroutine until the file is fully written to local storage, then reads
     * the file off disk.
     */
    private suspend fun downloadDocumentText(message: TdApi.Message): String {
        val doc  = (message.content as TdApi.MessageDocument).document
        val file = doc.document

        // If TDLib already has it cached, read immediately
        if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) {
            return File(file.local.path).readText()
        }

        // Start download (non-blocking request)
        client.send(TdApi.DownloadFile(file.id, 32, 0, 0, false))

        // Wait for completion via UpdateFile
        val completed = withTimeout(DOWNLOAD_TIMEOUT_MS) {
            client.updates
                .filterIsInstance<TdApi.UpdateFile>()
                .filter { it.file.id == file.id && it.file.local.isDownloadingCompleted }
                .first()
        }
        return File(completed.file.local.path).readText()
    }

    // ── Private — helpers ──────────────────────────────────────────────────────

    private suspend fun getSavedMessagesChatId(): Long {
        if (savedMessagesChatId != 0L) return savedMessagesChatId
        val me   = client.send(TdApi.GetMe()) as TdApi.User
        val chat = client.send(TdApi.CreatePrivateChat(me.id, false)) as TdApi.Chat
        savedMessagesChatId = chat.id
        return chat.id
    }
}

// ── Conversion extension functions ────────────────────────────────────────────

/** Converts a Room entity to a JSON index entry for [IndexRepository.pushIndex]. */
fun UploadedItemEntity.toIndexEntry(): VaultIndexEntry = VaultIndexEntry(
    id          = indexId,
    messageId   = telegramMessageId,
    filename    = displayName,
    sizeBytes   = sizeBytes,
    checksum    = sha256,
    takenAt     = Instant.ofEpochMilli(takenAt).toString(),
    uploadedAt  = Instant.ofEpochMilli(uploadedAt).toString(),
    mimeType    = mimeType,
    localUri    = localUri,
    chatId      = telegramChatId,
)

/**
 * Converts a JSON index entry back into a Room entity during [IndexRepository.syncFromRemote].
 *
 * [chatId] is the resolved Saved Messages chat ID for this session. Room entries
 * restored from the remote index have the same chat ID as when they were uploaded.
 */
fun VaultIndexEntry.toEntity(chatId: Long): UploadedItemEntity = UploadedItemEntity(
    indexId           = id,
    localUri          = localUri,
    displayName       = filename,
    sizeBytes         = sizeBytes,
    mimeType          = mimeType,
    sha256            = checksum,
    takenAt           = runCatching { Instant.parse(takenAt).toEpochMilli() }.getOrDefault(0L),
    telegramChatId    = if (this.chatId != 0L) this.chatId else chatId,
    telegramMessageId = messageId,
    uploadedAt        = runCatching { Instant.parse(uploadedAt).toEpochMilli() }.getOrDefault(0L),
)
