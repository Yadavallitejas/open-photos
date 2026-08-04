package com.qaxlabs.openphotos.ui.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/**
 * Shared OutlinedTextField colours for all auth screens.
 * Uses design-system tokens so the fields look native to the glass card:
 * - Unfocused border: Mist (onSurfaceVariant)
 * - Focused border: Signal (primary)
 * - Text: Ink (onSurface)
 * - Container: transparent (the GlassCard provides the background)
 */
@Composable
fun authFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    focusedLabelColor       = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor        = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
    cursorColor             = MaterialTheme.colorScheme.primary,
    errorBorderColor        = MaterialTheme.colorScheme.error,
    errorLabelColor         = MaterialTheme.colorScheme.error,
)
