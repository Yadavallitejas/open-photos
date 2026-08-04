package com.qaxlabs.openphotos.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.qaxlabs.openphotos.data.AuthState
import com.qaxlabs.openphotos.ui.components.GlassCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * First-run screen: collects api_id and api_hash.
 *
 * Design: Void background, centered glass card, short explainer, link to
 * my.telegram.org, Signal-pill "Continue". (design_system.md §10 point 1)
 */
@Composable
fun ApiCredentialsScreen(vm: AuthViewModel) {
    val authState by vm.authState.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    var apiId   by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }

    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Extract error message (if any) to show inline.
    val errorMessage = (authState as? AuthState.Error)?.message

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassCard {
                // ── Headline ──────────────────────────────────────────────
                Text(
                    text  = "Connect your\nTelegram account",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(12.dp))

                // ── Explainer ─────────────────────────────────────────────
                Text(
                    text  = "Open Photos uses the official Telegram API directly — " +
                            "your photos stay in your own account. " +
                            "You'll need a personal api_id and api_hash to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                // ── my.telegram.org link ──────────────────────────────────
                val linkText = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color          = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        )
                    ) {
                        append("Get yours at my.telegram.org →")
                    }
                }
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))
                        )
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(
                        text  = linkText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── api_id field ──────────────────────────────────────────
                OutlinedTextField(
                    value         = apiId,
                    onValueChange = { if (it.all { c -> c.isDigit() }) apiId = it },
                    label         = { Text("api_id") },
                    placeholder   = { Text("123456", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                // ── api_hash field ────────────────────────────────────────
                OutlinedTextField(
                    value         = apiHash,
                    onValueChange = { apiHash = it },
                    label         = { Text("api_hash") },
                    placeholder   = { Text("abc123def456…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            vm.onApiCredentialsSubmitted(apiId, apiHash)
                        }
                    ),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Inline error ──────────────────────────────────────────
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter   = fadeIn(),
                    exit    = fadeOut(),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = errorMessage ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Continue button ───────────────────────────────────────
                Button(
                    onClick  = { vm.onApiCredentialsSubmitted(apiId, apiHash) },
                    enabled  = !isLoading,
                    shape    = MaterialTheme.shapes.extraLarge,   // pill
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier   = Modifier.size(20.dp),
                            color      = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Continue", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
