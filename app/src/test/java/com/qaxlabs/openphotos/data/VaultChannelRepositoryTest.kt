package com.qaxlabs.openphotos.data

import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultChannelRepositoryTest {

    private class FakeSecureStore : SecureStore(DummyContext()) {
        var cachedId: Long? = null

        override suspend fun getVaultChannelId(): Long? = cachedId
        override suspend fun setVaultChannelId(id: Long) {
            cachedId = id
        }
        override suspend fun clearVaultChannelId() {
            cachedId = null
        }
    }

    private class FakeTelegramClient(
        val existingChats: List<TdApi.Chat> = emptyList(),
        val fullInfos: Map<Long, TdApi.SupergroupFullInfo> = emptyMap(),
        val histories: Map<Long, List<TdApi.Message>> = emptyMap(),
    ) : TelegramClient() {

        var createChannelCalled = false

        override suspend fun send(function: TdApi.Function<*>): TdApi.Object {
            return when (function) {
                is TdApi.SearchChatsOnServer -> {
                    val ids = existingChats.map { it.id }.toLongArray()
                    TdApi.Chats(ids.size, ids)
                }
                is TdApi.SearchChats -> {
                    TdApi.Chats(0, longArrayOf())
                }
                is TdApi.LoadChats -> {
                    TdApi.Ok()
                }
                is TdApi.GetChats -> {
                    TdApi.Chats(0, longArrayOf())
                }
                is TdApi.GetChat -> {
                    val chat = existingChats.find { it.id == function.chatId }
                    chat ?: throw TelegramException(404, "Chat not found")
                }
                is TdApi.GetSupergroupFullInfo -> {
                    val fullInfo = fullInfos[function.supergroupId.toLong()]
                    fullInfo ?: TdApi.SupergroupFullInfo()
                }
                is TdApi.GetChatHistory -> {
                    val list = histories[function.chatId] ?: emptyList()
                    TdApi.Messages(list.size, list.toTypedArray())
                }
                is TdApi.CreateNewSupergroupChat -> {
                    createChannelCalled = true
                    TdApi.Chat().apply {
                        id = 99999L
                        title = function.title
                        type = TdApi.ChatTypeSupergroup(888, true)
                    }
                }
                is TdApi.SetChatDescription -> {
                    TdApi.Ok()
                }
                else -> TdApi.Ok()
            }
        }
    }

    @Test
    fun getOrCreateVaultChannel_returnsCachedIdIfAvailable() = runBlocking {
        val secureStore = FakeSecureStore().apply { cachedId = 123L }
        val client = FakeTelegramClient()
        val repo = VaultChannelRepository(client, secureStore)

        val result = repo.getOrCreateVaultChannel()

        assertEquals(123L, result)
        assertFalse(client.createChannelCalled)
    }

    @Test
    fun getOrCreateVaultChannel_findsExistingChannelAndDoesNotCreateNewOne() = runBlocking {
        val secureStore = FakeSecureStore()
        val existingChat = TdApi.Chat().apply {
            id = 55555L
            title = VaultChannelRepository.VAULT_CHANNEL_TITLE
            type = TdApi.ChatTypeSupergroup(100, true)
        }
        val fullInfo = TdApi.SupergroupFullInfo().apply {
            description = VaultChannelRepository.VAULT_CHANNEL_MARKER
        }

        val client = FakeTelegramClient(
            existingChats = listOf(existingChat),
            fullInfos = mapOf(100L to fullInfo),
        )
        val repo = VaultChannelRepository(client, secureStore)

        val result = repo.getOrCreateVaultChannel()

        assertEquals(55555L, result)
        assertEquals(55555L, secureStore.cachedId)
        assertFalse(client.createChannelCalled)
    }

    @Test
    fun getOrCreateVaultChannel_createsNewChannelWhenNoExistingChannelFound() = runBlocking {
        val secureStore = FakeSecureStore()
        val client = FakeTelegramClient(existingChats = emptyList())
        val repo = VaultChannelRepository(client, secureStore)

        val result = repo.getOrCreateVaultChannel()

        assertEquals(99999L, result)
        assertEquals(99999L, secureStore.cachedId)
        assertTrue(client.createChannelCalled)
    }
}

// Dummy Context subclass for unit testing SecureStore without Android framework
private class DummyContext : android.content.ContextWrapper(null)
