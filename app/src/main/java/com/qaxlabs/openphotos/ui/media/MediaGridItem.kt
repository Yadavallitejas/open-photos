package com.qaxlabs.openphotos.ui.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.qaxlabs.openphotos.data.MediaItem
import com.qaxlabs.openphotos.ui.theme.Ink
import com.qaxlabs.openphotos.ui.theme.Signal

/**
 * A single cell in the [MediaScreen] photo/video grid.
 *
 * Design (design_system.md §9 — Grid cells):
 *  - Square 1:1 aspect ratio, Crop scaling.
 *  - Selected: Signal border (2 dp) + filled checkmark in top-right corner.
 *  - Unselected with active selection: dimmed to 85 % opacity.
 *  - Video items: semi-transparent play icon overlay in bottom-left.
 *
 * Coil 3 loads content:// URIs directly. Videos are decoded by the
 * coil-video module (first frame as thumbnail).
 */
@Composable
fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean,
    hasAnySelection: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dim unselected items while selection is active — smooth animation
    val alpha by animateFloatAsState(
        targetValue = if (!isSelected && hasAnySelection) 0.85f else 1f,
        label = "cellAlpha",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .alpha(alpha)
            .clip(RoundedCornerShape(2.dp))
            .then(
                if (isSelected)
                    Modifier.border(2.dp, Signal, RoundedCornerShape(2.dp))
                else
                    Modifier
            )
            .clickable(onClick = onToggle),
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────────
        AsyncImage(
            model           = item.uri,
            contentDescription = item.displayName,
            contentScale    = ContentScale.Crop,
            modifier        = Modifier.fillMaxSize(),
        )

        // ── Video play-icon badge ─────────────────────────────────────────
        if (item.isVideo) {
            Icon(
                imageVector        = Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint               = Color.White.copy(alpha = 0.85f),
                modifier           = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(20.dp),
            )
        }

        // ── Selection indicator (top-right) ───────────────────────────────
        if (isSelected) {
            // Filled Signal circle with checkmark
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Signal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint               = Ink,
                    modifier           = Modifier.size(14.dp),
                )
            }
        } else {
            // Empty ring — "tap to select" affordance
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape),
            )
        }
    }
}
