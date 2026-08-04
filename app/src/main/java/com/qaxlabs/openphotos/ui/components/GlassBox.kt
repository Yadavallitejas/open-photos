package com.qaxlabs.openphotos.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.qaxlabs.openphotos.ui.theme.Panel

/**
 * Universal Glass Container (design_system.md §5 & §10).
 *
 * Implements the frosted glass panel visual spec:
 *  - Panel base color (`#12141C`) at 88% opacity for crisp legibility and depth.
 *  - 1px top-edge highlight border (`rgba(255, 255, 255, 0.14)`).
 *  - Soft ambient drop shadow to lift floating chrome over Void background (`#0A0B10`).
 *  - Rounded corner shape clipping.
 *
 * Children composables (text, icons, buttons) render 100% sharp and un-blurred.
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val panelColor = Panel // = Panel token (#12141C)
    val bg = panelColor.copy(alpha = 0.88f)

    val topHighlight = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.14f),
        1f to Color.Transparent,
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.50f),
                spotColor = Color.Black.copy(alpha = 0.25f),
            )
            .clip(shape)
            .drawBehind { drawRect(color = bg) }
            .border(width = 1.dp, brush = topHighlight, shape = shape),
        content = content,
    )
}
