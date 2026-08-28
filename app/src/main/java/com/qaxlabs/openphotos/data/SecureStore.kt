package com.qaxlabs.openphotos.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

// Extension property: single DataStore instance per process (DataStore guarantees
// exactly-once initialisation via the delegate).
private val Context.secureDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "openphotos_secure")

/**
 * Secure credential store backed by DataStore + Android Keystore (AES-256/GCM).
 *
 * Why not EncryptedSharedPreferences?
 * EncryptedSharedPreferences is deprecated as of security-crypto 1.1.0-alpha07
 * due to StrictMode violations and keyset-corruption bugs. This implementation
 * uses the same Android Keystore underneath — with identical security guarantees
 * — but is fully coroutine-native and has no known reliability issues.
 *
 * Migration path (v2): add Tink for additional key-derivation options if needed.
 *
 * Stores: api_id, api_hash, phone_number, tdlib_db_key.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "openphotos_v1_aes_key"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128

        private val KEY_API_ID           = stringPreferencesKey("enc_api_id")
        private val KEY_API_HASH         = stringPreferencesKey("enc_api_hash")
        private val KEY_PHONE            = stringPreferencesKey("enc_phone")
        private val KEY_TDLIB_DB         = stringPreferencesKey("enc_tdlib_db_key")
        private val KEY_INDEX_MSG_ID     = stringPreferencesKey("enc_index_msg_id")
        private val KEY_VAULT_CHANNEL_ID = stringPreferencesKey("enc_vault_channel_id")
    }

    // ── Android Keystore AES-256/GCM key ─────────────────────────────────────

    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }

    private fun encryptionKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    // ── Encrypt / Decrypt helpers ─────────────────────────────────────────────

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val iv         = cipher.iv                              // 12 bytes
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined   = iv + ciphertext                        // IV prepended
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined   = Base64.decode(encoded, Base64.NO_WRAP)
        val iv         = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher     = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    val apiIdFlow: Flow<Int?> = context.secureDataStore.data.map { prefs ->
        prefs[KEY_API_ID]?.let { runCatching { decrypt(it).toIntOrNull() }.getOrNull() }
    }

    val apiHashFlow: Flow<String?> = context.secureDataStore.data.map { prefs ->
        prefs[KEY_API_HASH]?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    suspend fun setApiId(apiId: Int) {
        context.secureDataStore.edit { it[KEY_API_ID] = encrypt(apiId.toString()) }
    }

    suspend fun getApiId(): Int? =
        context.secureDataStore.data.first()[KEY_API_ID]?.let {
            runCatching { decrypt(it).toIntOrNull() }.getOrNull()
        }

    suspend fun setApiHash(apiHash: String) {
        context.secureDataStore.edit { it[KEY_API_HASH] = encrypt(apiHash) }
    }

    suspend fun getApiHash(): String? =
        context.secureDataStore.data.first()[KEY_API_HASH]?.let {
            runCatching { decrypt(it) }.getOrNull()
        }

    suspend fun setApiCredentials(apiId: Int, apiHash: String) {
        context.secureDataStore.edit { prefs ->
            prefs[KEY_API_ID] = encrypt(apiId.toString())
            prefs[KEY_API_HASH] = encrypt(apiHash)
        }
    }

    suspend fun setPhoneNumber(phone: String) {
        context.secureDataStore.edit { it[KEY_PHONE] = encrypt(phone) }
    }

    suspend fun getPhoneNumber(): String? =
        context.secureDataStore.data.first()[KEY_PHONE]?.let { decrypt(it) }

    /**
     * Returns the 32-byte key used to encrypt TDLib's own on-disk database.
     * Generated once and persisted across app launches so TDLib can re-open
     * its existing session database. See tech_stack.md §6.
     */
    suspend fun tdlibDatabaseKey(): ByteArray {
        val stored = context.secureDataStore.data.first()[KEY_TDLIB_DB]
        if (stored != null) {
            val keyB64 = decrypt(stored)
            return Base64.decode(keyB64, Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val keyB64 = Base64.encodeToString(key, Base64.NO_WRAP)
        context.secureDataStore.edit { it[KEY_TDLIB_DB] = encrypt(keyB64) }
        return key
    }

    /** The Telegram message ID of the vault index Document in the vault channel. */
    suspend fun getIndexMessageId(): Long? =
        context.secureDataStore.data.first()[KEY_INDEX_MSG_ID]
            ?.let { decrypt(it).toLongOrNull() }

    suspend fun setIndexMessageId(id: Long) {
        context.secureDataStore.edit { it[KEY_INDEX_MSG_ID] = encrypt(id.toString()) }
    }

    suspend fun clearIndexMessageId() {
        context.secureDataStore.edit { it.remove(KEY_INDEX_MSG_ID) }
    }

    /**
     * The Telegram chat ID of the dedicated "OpenPhotos Vault" broadcast channel
     * (FR-INDEX-0). Cached here so [VaultChannelRepository] avoids a chat-list
     * scan on every cold start.
     */
    suspend fun getVaultChannelId(): Long? =
        context.secureDataStore.data.first()[KEY_VAULT_CHANNEL_ID]
            ?.let { decrypt(it).toLongOrNull() }

    suspend fun setVaultChannelId(id: Long) {
        context.secureDataStore.edit { it[KEY_VAULT_CHANNEL_ID] = encrypt(id.toString()) }
    }

    suspend fun clearVaultChannelId() {
        context.secureDataStore.edit { it.remove(KEY_VAULT_CHANNEL_ID) }
    }

    suspend fun clear() {
        context.secureDataStore.edit { prefs ->
            // FR-AUTH-5: Preserves API ID, API Hash, and the TDLib DB key.
            // These are tied to the app installation, not the account session.
            val apiIdEnc   = prefs[KEY_API_ID]
            val apiHashEnc  = prefs[KEY_API_HASH]
            val dbKeyEnc    = prefs[KEY_TDLIB_DB]

            prefs.clear()

            if (apiIdEnc != null) prefs[KEY_API_ID] = apiIdEnc
            if (apiHashEnc != null) prefs[KEY_API_HASH] = apiHashEnc
            if (dbKeyEnc != null) prefs[KEY_TDLIB_DB] = dbKeyEnc
        }
    }
}
