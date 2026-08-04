package com.qaxlabs.openphotos.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.AuthState
import com.qaxlabs.openphotos.data.TelegramAuthRepository
import com.qaxlabs.openphotos.data.TelegramException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the entire auth flow.
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This class does NOT import or reference any TDLib class.
 * All Telegram operations are delegated to [TelegramAuthRepository].
 *
 * Shared across all auth screens via a nested navigation graph so that
 * each screen sees the same instance (see [AppNavGraph]).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: TelegramAuthRepository,
) : ViewModel() {

    /** Mirrors [TelegramAuthRepository.authState] — the single source of truth. */
    val authState: StateFlow<AuthState> = repository.authState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Credentials Screen ─────────────────────────────────────────────────

    fun onApiCredentialsSubmitted(apiIdRaw: String, apiHash: String) {
        val apiId = apiIdRaw.trim().toIntOrNull()
        if (apiId == null || apiId <= 0) {
            repository.emitError("api_id must be a positive integer.")
            return
        }
        if (apiHash.isBlank()) {
            repository.emitError("api_hash cannot be empty.")
            return
        }
        launchAuth { repository.initTdLib(apiId, apiHash.trim()) }
    }

    // ── Phone Screen ───────────────────────────────────────────────────────

    fun onPhoneSubmitted(phone: String) {
        if (phone.isBlank()) {
            repository.emitError("Please enter your phone number.")
            return
        }
        launchAuth { repository.setPhoneNumber(phone.trim()) }
    }

    // ── OTP Screen ─────────────────────────────────────────────────────────

    fun onCodeSubmitted(code: String) {
        if (code.isBlank()) {
            repository.emitError("Please enter the verification code.")
            return
        }
        launchAuth { repository.submitCode(code.trim()) }
    }

    fun onBackToPhoneRequested() {
        repository.resetToPhoneEntry()
    }

    fun onBackToCredentialsRequested() {
        repository.resetToCredentials()
    }

    // ── 2FA Password Screen ────────────────────────────────────────────────

    fun onPasswordSubmitted(password: String) {
        if (password.isEmpty()) {
            repository.emitError("Please enter your cloud password.")
            return
        }
        launchAuth { repository.submitPassword(password) }
    }

    // ── Error handling & session ───────────────────────────────────────────

    fun clearError() = repository.clearError()

    fun logout() {
        launchAuth { repository.logout() }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Wraps an auth operation: sets isLoading, runs [block], maps
     * [TelegramException] → [AuthState.Error] without exposing TDLib types.
     */
    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                block()
            } catch (e: TelegramException) {
                repository.emitError(e.message ?: "Unknown Telegram error")
            } catch (e: Exception) {
                repository.emitError(e.message ?: "Unexpected error")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
