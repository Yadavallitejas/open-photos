package com.qaxlabs.openphotos.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Low-level TDLib client wrapper.
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * This is the ONLY class in the entire codebase permitted to touch
 * [org.drinkless.tdlib.Client] directly. [TelegramAuthRepository] calls this;
 * nothing else should.
 *
 * All TDLib updates are re-emitted as a [SharedFlow] so the rest of the app
 * can consume them as a stream without ever touching raw TDLib callbacks.
 */
@Singleton
open class TelegramClient @Inject constructor() {

    private val _updates = MutableSharedFlow<TdApi.Object>(
        replay = 1,
        extraBufferCapacity = 128,
    )

    /** Stream of every raw TDLib update object. Collect in a coroutine. */
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()

    @Volatile
    private var client: Client? = null

    open val isInitialized: Boolean
        get() = client != null

    /**
     * Constructs the native TDLib client and starts the update pump.
     * Must be called once before any [send] calls.
     */
    open fun create() {
        if (client != null) return
        client = Client.create(
            /* updateHandler          */ { update -> _updates.tryEmit(update) },
            /* updateExceptionHandler */ null,
            /* defaultExceptionHandler*/ null,
        )
    }

    /**
     * Suspends until TDLib processes [function] and returns its result.
     * Throws [TelegramException] if TDLib returns [TdApi.Error].
     */
    open suspend fun send(function: TdApi.Function<*>): TdApi.Object =
        suspendCancellableCoroutine { cont ->
            val c = client
            if (c == null) {
                cont.resumeWithException(IllegalStateException("TDLib client not initialized"))
                return@suspendCancellableCoroutine
            }
            c.send(function) { result ->
                if (cont.isActive) {
                    if (result is TdApi.Error) {
                        cont.resumeWithException(TelegramException(result.code, result.message))
                    } else {
                        cont.resume(result)
                    }
                }
            }
        }

    /**
     * Executes a synchronous TDLib function (only a handful exist, e.g.
     * [TdApi.GetTextEntities]). Most callers should use [send] instead.
     */
    fun executeSync(function: TdApi.Function<*>): TdApi.Object? =
        Client.execute(function)

    fun close() {
        // TDLib's Client object doesn't expose a close() method directly.
        // Send the TdApi.Close request to initiate a clean shutdown; TDLib
        // will emit AuthorizationStateClosed which resets our state machine.
        client?.send(TdApi.Close()) { /* ignore result */ }
        client = null
    }

    /**
     * Resets the native client reference to null so a subsequent [create] call
     * can instantiate a fresh native TDLib Client instance.
     */
    fun destroy() {
        client = null
    }
}

/** Wraps a TDLib error code + message as a typed Kotlin exception. */
class TelegramException(val code: Int, message: String) :
    Exception("TDLib error $code: $message")
