package com.qaxlabs.openphotos.ui.components

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(24.dp)

/**
 * Glass card component — the signature visual element for auth screens and
 * floating chrome (design_system.md §5 and §10).
 *
 * Implementation notes (design_system.md §5):
 *   • API 31+: real backdrop blur is not applied here because auth screens
 *     have a solid Void background (nothing to blur). The Panel colour at
 *     40 % alpha + 1 px top-edge highlight produces the correct glass look.
 *   • API 26–30: same card, Panel at 88 % alpha — a slightly simplified
 *     sibling, not a broken fallback.
 *   • A very soft ambient shadow beneath the card separates it from the
 *     background without adding visual weight.
 *
 * For future screens where the card floats over live photo content, add a
 * RenderEffect blur layer (API 31+) around this composable at the call site.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val panelColor = MaterialTheme.colorScheme.surface  // = Panel token

    // Alpha: 40 % on API 31+ (glass-like), 88 % on API 26–30 (opaque sibling)
    val bgAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.40f else 0.88f
    val bg      = panelColor.copy(alpha = bgAlpha)

    // Top-edge highlight gradient: rgba(255,255,255,0.14) → transparent
    // This 1 px line reads as a "glass edge" catching light.
    val highlight = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.14f),
        1f to Color.Transparent,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            // Soft ambient shadow — lifts the card off the Void background
            .shadow(
                elevation         = 24.dp,
                shape             = CardShape,
                ambientColor      = Color.Black.copy(alpha = 0.60f),
                spotColor         = Color.Black.copy(alpha = 0.30f),
            )
            .clip(CardShape)
            // Panel colour fill
            .drawBehind { drawRect(color = bg) }
            // 1 dp top-edge highlight border
            .border(width = 1.dp, brush = highlight, shape = CardShape)
            .padding(24.dp),
        content = content,
    )
}
