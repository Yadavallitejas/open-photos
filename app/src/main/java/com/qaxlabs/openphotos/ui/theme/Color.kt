package com.qaxlabs.openphotos.ui.theme

import androidx.compose.ui.graphics.Color

// ── Design System Color Tokens ────────────────────────────────────────────────
// Source of truth: docs/design_system.md §2
// Six named tokens, used deliberately — not decorative.

/** Base background (dark mode). Everything else sits on top of this. */
val Void    = Color(0xFF0A0B10)

/** Base surface for glass panels, before blur/alpha treatment. */
val Panel   = Color(0xFF12141C)

/** Primary text / icons. */
val Ink     = Color(0xFFF4F5FA)

/** Secondary text, timestamps, captions. */
val Mist    = Color(0xFF8B90A3)

/**
 * Primary accent — CTAs, selection rings, active nav states.
 * Appears on at most ONE primary action per screen.
 */
val Signal  = Color(0xFF6E5BFF)

/**
 * Secondary accent — backup / sync states ONLY (Sync Halo, progress bars,
 * "backed up" indicators). Using it elsewhere dilutes its meaning.
 */
val Aurora  = Color(0xFF3FE0C5)

/** Error / destructive — the only additional functional colour. */
val Ember   = Color(0xFFFF6B6B)

// ── Light mode equivalents ────────────────────────────────────────────────────
val Paper        = Color(0xFFF6F7FB)
val PanelLight   = Color(0xFFFFFFFF)   // Used at 80–90 % opacity over Paper
val InkLight     = Color(0xFF14161F)
val MistLight    = Color(0xFF6B7086)