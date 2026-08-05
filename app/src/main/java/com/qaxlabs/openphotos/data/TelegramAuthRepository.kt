package com.qaxlabs.openphotos.data

import android.content.Context
import android.os.Build
import com.qaxlabs.openphotos.BuildConfig
import com.qaxlabs.openphotos.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// ── Auth State ────────────────────────────────────────────────────────────────

/**
 * Represents every possible state the MTProto auth flow can be in.
 * The UI layer observes this and navigates / renders accordingly.
 */
sealed class AuthState {
    /** TDLib client is starting; waiting for the first update from TDLib. */
    object Initializing : AuthState()

    /**
     * TDLib needs api_id / api_hash but none are saved yet (first run).
     * Navigate to the API credentials screen.
     */
    object WaitingCredentials : AuthState()

    /** Credentials accepted; TDLib is waiting for the user's phone number. */
    object WaitingPhoneNumber : AuthState()

    /** Phone submitted; TDLib is waiting for the OTP code. */
    object WaitingCode : AuthState()

    /**
     * OTP accepted but this account has 2FA enabled.
     * TDLib is waiting for the cloud password.
     */
    object WaitingPassword : AuthState()

    /** Full authentication complete. [phoneNumber] is E.164 ("+…"). */
    data class Authenticated(val phoneNumber: String) : AuthState()

    /** A recoverable error. The UI should surface [message] and stay on its screen. */
    data class Error(val message: String) : AuthState()
}

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Single source of truth for the Telegram authentication domain.
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This is the ONLY class permitted to call [TelegramClient] (and therefore
 * TDLib) directly. ViewModels interact exclusively through this interface.
 */
@Singleton
class TelegramAuthRepository @Inject constructor(
    private val client: TelegramClient,
    private val secureStore: SecureStore,
    private val indexRepository: IndexRepository,
    private val vaultChannelRepository: VaultChannelRepository,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        scope.launch {
            // Start the TDLib client; updates begin flowing immediately.
            client.create()

            // Collect every TDLib update and forward auth-state changes.
            client.updates.collect { update ->
                if (update is TdApi.UpdateAuthorizationState) {
                    handleAuthorizationState(update.authorizationState)
                }
            }
        }
    }

    // ── Internal state machine ─────────────────────────────────────────────

    private suspend fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                // Try to auto-initialise from saved credentials (subsequent launches).
                val savedId   = secureStore.getApiId()
                val savedHash = secureStore.getApiHash()
                if (savedId != null && savedHash != null) {
                    // Stay in Initializing — the next state update will navigate the UI.
                    runCatching { initTdLib(savedId, savedHash) }
                        .onFailure { _authState.value = AuthState.Error(it.message ?: "Init failed") }
                } else {
                    // First run — ask the user for credentials.
                    _authState.value = AuthState.WaitingCredentials
                }
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = AuthState.WaitingPhoneNumber
            }

            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = AuthState.WaitingCode
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = AuthState.WaitingPassword
            }

            is TdApi.AuthorizationStateReady -> {
                // Fetch the user's phone number to display on the success screen.
                val phone = try {
                    val me = client.send(TdApi.GetMe()) as TdApi.User
                    // TDLib returns the phone without the leading "+".
                    "+${me.phoneNumber}"
                } catch (_: Exception) {
                    secureStore.getPhoneNumber() ?: "unknown"
                }
                secureStore.setPhoneNumber(phone)
                _authState.value = AuthState.Authenticated(phone)

                // FR-INDEX-0: ensure the vault channel exists before any index
                // operation (find existing or create new). Runs in a separate
                // coroutine so it never delays the auth state update itself.
                // FR-INDEX-2: pull remote index + hydrate Room after the channel
                // is confirmed. Sequential: channel first, then sync.
                scope.launch {
                    vaultChannelRepository.getOrCreateVaultChannel()
                    indexRepository.syncFromRemote()
                }
            }

            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing -> {
                _authState.value = AuthState.Initializing
            }

            is TdApi.AuthorizationStateClosed -> {
                _authState.value = AuthState.Initializing
                // Recreate the client after a clean close (e.g. logout).
                client.create()
            }

            else -> { /* other states (e-mail code etc.) not handled in v1 */ }
        }
    }

    // ── Public API called by AuthViewModel ────────────────────────────────

    /**
     * Sets TDLib parameters and persists the credentials.
     * Called once on first run when the user submits api_id + api_hash.
     */
    suspend fun initTdLib(apiId: Int, apiHash: String) {
        secureStore.setApiId(apiId)
        secureStore.setApiHash(apiHash)

        val dbKey = secureStore.tdlibDatabaseKey()
        val dbDir = context.filesDir.absolutePath + "/tdlib"

        val params = TdApi.SetTdlibParameters().also { p ->
            p.useTestDc             = false
            p.databaseDirectory     = dbDir
            p.filesDirectory        = dbDir
            p.databaseEncryptionKey = dbKey
            p.useFileDatabase       = true
            p.useChatInfoDatabase   = true
            p.useMessageDatabase    = true
            p.useSecretChats        = false
            p.apiId                 = apiId
            p.apiHash               = apiHash
            p.systemLanguageCode    = Locale.getDefault().toLanguageTag()
            p.deviceModel           = Build.MODEL
            p.systemVersion         = Build.VERSION.RELEASE
            p.applicationVersion    = BuildConfig.VERSION_NAME
        }

        client.send(params)
    }

    /** Submits the user's phone number (E.164 recommended, TDLib is lenient). */
    suspend fun setPhoneNumber(phone: String) {
        client.send(TdApi.SetAuthenticationPhoneNumber(phone, null))
    }

    /** Submits the OTP received via Telegram app or SMS. */
    suspend fun submitCode(code: String) {
        client.send(TdApi.CheckAuthenticationCode(code))
    }

    /** Submits the 2FA cloud password (only required when [AuthState.WaitingPassword]). */
    suspend fun submitPassword(password: String) {
        client.send(TdApi.CheckAuthenticationPassword(password))
    }

    /** Resets the auth flow to the phone number entry stage. */
    fun resetToPhoneEntry() {
        _authState.value = AuthState.WaitingPhoneNumber
    }

    /** Resets the auth flow to the API credentials entry stage. */
    fun resetToCredentials() {
        _authState.value = AuthState.WaitingCredentials
    }

    /** Emits an [AuthState.Error] without touching TDLib (e.g. for local validation). */
    fun emitError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    /** Clears the current error and restores the previous waiting state. */
    fun clearError() {
        // Re-request the current TDLib auth state so the flow self-heals.
        scope.launch {
            try {
                val result = client.send(TdApi.GetAuthorizationState())
                handleAuthorizationState(result as TdApi.AuthorizationState)
            } catch (_: Exception) { }
        }
    }

    /** Logs out of Telegram and clears saved session credentials. */
    suspend fun logout() {
        try {
            client.send(TdApi.LogOut())
        } catch (_: Exception) {}
        // Clear cached vault channel ID so a fresh login re-validates the channel.
        vaultChannelRepository.clearCachedChannelId()
        secureStore.clear()
    }
}
