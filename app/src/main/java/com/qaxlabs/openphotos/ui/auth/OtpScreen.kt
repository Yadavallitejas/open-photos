package com.qaxlabs.openphotos.ui.auth

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qaxlabs.openphotos.data.AuthState
import com.qaxlabs.openphotos.ui.components.GlassCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * OTP verification screen.
 *
 * Auto-submits when the user enters 5 digits (standard Telegram OTP length).
 * Falls back to a manual "Verify" button for edge cases.
 * (design_system.md §10 point 2)
 */
@Composable
fun OtpScreen(vm: AuthViewModel) {
    val authState by vm.authState.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    var code by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val errorMessage = (authState as? AuthState.Error)?.message

    // Auto-submit once 5 digits are entered.
    LaunchedEffect(code) {
        if (code.length == 5) {
            focusManager.clearFocus()
            vm.onCodeSubmitted(code)
        }
    }

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
                Text(
                    text  = "Verification code",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text  = "Enter the code Telegram sent to your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                // Large, centred OTP field.
                OutlinedTextField(
                    value         = code,
                    onValueChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) code = it },
                    singleLine    = true,
                    textStyle     = LocalTextStyle.current.copy(
                        fontSize  = 32.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = 12.sp,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            vm.onCodeSubmitted(code)
                        }
                    ),
                    colors   = authFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                )

                AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = errorMessage ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick  = { vm.onCodeSubmitted(code) },
                    enabled  = !isLoading && code.length == 5,
                    shape    = MaterialTheme.shapes.extraLarge,
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
                            modifier    = Modifier.size(20.dp),
                            color       = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Verify", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = { vm.onBackToPhoneRequested() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Change phone number",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
