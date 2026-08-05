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
     * Walks up to [MAX_SCAN_PAGES] × [CHAT_PAGE_SIZE] chats in the user's
     * loaded chat list, looking for one whose title and description both match
     * the vault channel spec.
     *
     * TDLib's [TdApi.GetChats] returns IDs in order of recent activity.
     * We pass the last-seen chat's order/id as the cursor for paging.
     */
    private suspend fun scanChatListForVaultChannel(): Long? {
        var offsetOrder  = Long.MAX_VALUE
        var offsetChatId = 0L

        repeat(MAX_SCAN_PAGES) { page ->
            val result = runCatching {
                val req = TdApi.GetChats(
                    TdApi.ChatListMain(),
                    CHAT_PAGE_SIZE,
                )
                client.send(req) as TdApi.Chats
            }.getOrNull() ?: return null

            if (result.chatIds.isEmpty()) return null  // exhausted the list

            for (chatId in result.chatIds) {
                val chat = runCatching {
                    client.send(TdApi.GetChat(chatId)) as TdApi.Chat
                }.getOrNull() ?: continue

                // Only broadcast channels have ChatTypeSupergroup with isChannel = true.
                val type = chat.type
                if (type !is TdApi.ChatTypeSupergroup || !type.isChannel) continue
                if (chat.title != VAULT_CHANNEL_TITLE) continue

                // Title matches — fetch full info to check the About/description.
                val fullInfo = runCatching {
                    client.send(TdApi.GetSupergroupFullInfo(type.supergroupId)) as TdApi.SupergroupFullInfo
                }.getOrNull() ?: continue

                if (fullInfo.description == VAULT_CHANNEL_MARKER) {
                    return chatId   // ✓ both title and marker match
                }
            }

            Log.d(TAG, "Scan page $page: ${result.chatIds.size} chats checked, no match yet")
            // No cursor needed for GetChats — TDLib manages pagination internally
            // when called repeatedly; break after one scan (TDLib loads lazily).
            if (result.chatIds.size < CHAT_PAGE_SIZE) return null  // last page
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
