package com.qaxlabs.openphotos.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Colour Schemes ────────────────────────────────────────────────────────────
// design_system.md §2: Dark mode is the primary and default mode for the vault.
// Dynamic colour is disabled — the vault's deliberate palette (Void #0A0B10,
// Panel #12141C) must not be overridden by Material You wallpaper extraction.

private val DarkColorScheme = darkColorScheme(
    primary          = Signal,
    onPrimary        = Ink,
    primaryContainer = Panel,
    secondary        = Aurora,
    onSecondary      = Void,
    background       = Void,
    onBackground     = Ink,
    surface          = Panel,
    onSurface        = Ink,
    surfaceVariant   = Panel,
    onSurfaceVariant = Mist,
    error            = Ember,
    onError          = Ink,
    outline          = Mist,
)

@Composable
fun OpenPhotosTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Dark mode is the primary and only visual mode for v1 (design_system.md §2).
    val colorScheme = DarkColorScheme

    // Push Void into status & navigation bars for seamless edge-to-edge drawing.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}