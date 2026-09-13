package com.qaxlabs.openphotos.data

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the dedicated private broadcast channel used as the vault's storage
 * location for both uploaded Documents and the JSON index (FR-INDEX-0).
 *
 * Channel spec (FR-INDEX-0):
 *   Title   : "OpenPhotos Vault"   (exact, case-sensitive)
 *   About   : "vault-marker:openphotos-v1"   (exact)
 *   Type    : private broadcast channel (isChannel = true), user as sole owner.
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This is the ONLY class (besides [TelegramAuthRepository], [UploadRepository],
 * and [IndexRepository]) permitted to call [TelegramClient] directly.
 *
 * ### Find-or-create strategy
 * 1. Return [SecureStore.getVaultChannelId] if cached (O(1), no network).
 * 2. Walk the user's loaded chat list looking for a supergroup/channel whose
 *    title matches [VAULT_CHANNEL_TITLE] AND whose full-info description matches
 *    [VAULT_CHANNEL_MARKER].
 * 3. If still not found: create a new private broadcast channel, set its
 *    description to [VAULT_CHANNEL_MARKER], cache and return its chat ID.
 *
 * All three steps are serialised behind [resolveMutex] so concurrent callers
 * (e.g. UploadRepository + IndexRepository racing at login) don't create
 * duplicate channels.
 */
@Singleton
class VaultChannelRepository @Inject constructor(
    private val client: TelegramClient,
    private val secureStore: SecureStore,
) {
    companion object {
        private const val TAG = "VaultChannelRepository"

        /** Exact channel title required by FR-INDEX-0. */
        const val VAULT_CHANNEL_TITLE = "OpenPhotos Vault"

        /** Exact About/description marker required by FR-INDEX-0. */
        const val VAULT_CHANNEL_MARKER = "vault-marker:openphotos-v1"

        /** How many chat IDs to load per GetChats page when scanning. */
        private const val CHAT_PAGE_SIZE = 100

        /** Maximum pages to scan before giving up and creating a new channel. */
        private const val MAX_SCAN_PAGES = 5
    }

    /** Serialises concurrent find-or-create calls. */
    private val resolveMutex = Mutex()

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the chat ID of the vault broadcast channel, creating it if needed.
     *
     * Suspend-safe: multiple callers block on [resolveMutex]; only the first
     * performs network I/O and caches the result — subsequent callers return
     * the cached value immediately.
     */
    suspend fun getOrCreateVaultChannel(): Long = resolveMutex.withLock {
        // 1. Fast path: cached across launches in SecureStore.
        val cached = secureStore.getVaultChannelId()
        if (cached != null) {
            Log.d(TAG, "Vault channel ID from cache: $cached")
            return@withLock cached
        }

        // 2. Scan the loaded chat list for an existing matching channel.
        val found = scanChatListForVaultChannel()
        if (found != null) {
            secureStore.setVaultChannelId(found)
            Log.i(TAG, "Found existing vault channel: $found")
            return@withLock found
        }

        // 3. No match — create a fresh private broadcast channel.
        val created = createVaultChannel()
        secureStore.setVaultChannelId(created)
        Log.i(TAG, "Created new vault channel: $created")
        return@withLock created
    }

    /**
     * Clears the cached channel ID from [SecureStore].
     * Called on logout so the next login re-validates the channel.
     */
    suspend fun clearCachedChannelId() {
        secureStore.clearVaultChannelId()
    }

    // ── Private — scan ──────────────────────────────────────────────────────

    /**
     * Finds an existing vault channel across all available search strategies:
     * 1. Server-side chat search ([TdApi.SearchChatsOnServer])
     * 2. Local chat search ([TdApi.SearchChats])
     * 3. Paginated chat list loading ([TdApi.LoadChats] + [TdApi.GetChats])
     *
     * If multiple candidates exist (e.g. from prior duplicate creations),
     * selects the candidate with the highest score (prioritising channels
     * containing the index JSON file or non-empty history).
     */
    private suspend fun scanChatListForVaultChannel(): Long? {
        val candidateIds = LinkedHashSet<Long>()

        // Strategy 1: Search Telegram servers directly for chats titled "OpenPhotos Vault"
        runCatching {
            val res = client.send(TdApi.SearchChatsOnServer(VAULT_CHANNEL_TITLE, 20)) as TdApi.Chats
            candidateIds.addAll(res.chatIds.toList())
        }.onFailure { Log.w(TAG, "SearchChatsOnServer failed: ${it.message}") }

        // Strategy 2: Search local chat database
        runCatching {
            val res = client.send(TdApi.SearchChats(VAULT_CHANNEL_TITLE, 20)) as TdApi.Chats
            candidateIds.addAll(res.chatIds.toList())
        }.onFailure { Log.w(TAG, "SearchChats failed: ${it.message}") }

        // Strategy 3: Load and scan chat list pages from TDLib
        repeat(MAX_SCAN_PAGES) {
            runCatching {
                client.send(TdApi.LoadChats(TdApi.ChatListMain(), CHAT_PAGE_SIZE))
            }
            val chatsResult = runCatching {
                client.send(TdApi.GetChats(TdApi.ChatListMain(), CHAT_PAGE_SIZE)) as TdApi.Chats
            }.getOrNull()

            if (chatsResult != null && chatsResult.chatIds.isNotEmpty()) {
                candidateIds.addAll(chatsResult.chatIds.toList())
            }
        }

        if (candidateIds.isEmpty()) return null

        var bestChatId: Long? = null
        var bestScore = -1

        for (chatId in candidateIds) {
            val chat = runCatching {
                client.send(TdApi.GetChat(chatId)) as TdApi.Chat
            }.getOrNull() ?: continue

            val type = chat.type
            if (type !is TdApi.ChatTypeSupergroup || !type.isChannel) continue
            if (chat.title != VAULT_CHANNEL_TITLE) continue

            val fullInfo = runCatching {
                client.send(TdApi.GetSupergroupFullInfo(type.supergroupId)) as TdApi.SupergroupFullInfo
            }.getOrNull() ?: continue

            val hasMarker = fullInfo.description.contains(VAULT_CHANNEL_MARKER)

            // Check history for score
            val history = runCatching {
                val req = TdApi.GetChatHistory().also { r ->
                    r.chatId = chatId
                    r.fromMessageId = 0L
                    r.offset = 0
                    r.limit = 10
                    r.onlyLocal = false
                }
                client.send(req) as TdApi.Messages
            }.getOrNull()

            val hasIndexFile = history?.messages?.any { msg ->
                val content = msg.content
                content is TdApi.MessageDocument && content.document.fileName == VaultIndex.INDEX_FILENAME
            } ?: false

            val hasMessages = history?.messages?.isNotEmpty() == true

            var score = 0
            if (hasMarker && hasIndexFile) {
                score = 3
            } else if (hasMarker && hasMessages) {
                score = 2
            } else if (hasMarker) {
                score = 1
            } else {
                // Title and channel match, but description marker missing
                score = 0
            }

            if (score > bestScore) {
                bestScore = score
                bestChatId = chatId
                if (score == 3) break // Found the ideal vault channel
            }
        }

        if (bestChatId != null) {
            // Repair marker if needed
            if (bestScore == 0) {
                runCatching {
                    client.send(TdApi.SetChatDescription(bestChatId, VAULT_CHANNEL_MARKER))
                }
            }
            return bestChatId
        }

        return null
    }

    // ── Private — create ────────────────────────────────────────────────────

    /**
     * Creates a new private broadcast channel titled [VAULT_CHANNEL_TITLE]
     * and sets its description to [VAULT_CHANNEL_MARKER].
     *
     * @return the new channel's TDLib chat ID.
     * @throws TelegramException if TDLib returns an error.
     */
    private suspend fun createVaultChannel(): Long {
        // Step A: create the channel (initially no description — set separately).
        val createReq = TdApi.CreateNewSupergroupChat(
            /* title         */ VAULT_CHANNEL_TITLE,
            /* isChannel     */ true,           // broadcast channel, not a group
            /* isForum       */ false,
            /* description   */ VAULT_CHANNEL_MARKER,
            /* location      */ null,
            /* messageAutoDeleteTime */ 0,
            /* forImport     */ false,
        )
        val chat = client.send(createReq) as TdApi.Chat
        val chatId = chat.id
        Log.i(TAG, "Created vault broadcast channel chatId=$chatId title='${chat.title}'")

        // Step B: set the description / About text to the marker string so it
        // survives a fresh-install scan (the marker is what distinguishes this
        // channel from any other the user might have named the same thing).
        // CreateNewSupergroupChat accepts a description, but we set it again via
        // SetChatDescription as a belt-and-suspenders measure in case the field
        // is not honoured on creation in all TDLib versions.
        runCatching {
            client.send(TdApi.SetChatDescription(chatId, VAULT_CHANNEL_MARKER))
        }.onFailure { e ->
            Log.w(TAG, "SetChatDescription after create failed (non-fatal): ${e.message}")
        }

        return chatId
    }
}
