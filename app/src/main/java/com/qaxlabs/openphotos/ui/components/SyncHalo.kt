package com.qaxlabs.openphotos.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.qaxlabs.openphotos.ui.theme.Aurora

/**
 * Signature Element: The Sync Halo (design_system.md §8 & §9).
 *
 * Rotating & pulsing Aurora-colored gradient ring during active upload.
 * On completion, contracts into a small Aurora dot with a checkmark in the bottom-right corner.
 */
@Composable
fun SyncHalo(
    isUploading: Boolean,
    isBackedUp: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isUploading) {
        val infiniteTransition = rememberInfiniteTransition(label = "SyncHaloTransition")

        // Slow rotation (360 degrees in 3000ms)
        val rotationDegrees by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "Rotation",
        )

        // Soft pulse (opacity 0.6f -> 1.0f -> 0.6f over 1500ms)
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "Pulse",
        )

        val haloGradient = Brush.sweepGradient(
            colors = listOf(
                Aurora.copy(alpha = 0.1f * pulseAlpha),
                Aurora.copy(alpha = pulseAlpha),
                Aurora.copy(alpha = 0.3f * pulseAlpha),
                Aurora.copy(alpha = 0.9f * pulseAlpha),
                Aurora.copy(alpha = 0.1f * pulseAlpha),
            )
        )

        Canvas(
            modifier = modifier
                .fillMaxSize()
                .rotate(rotationDegrees)
                .padding(2.dp)
        ) {
            val strokeWidth = 2.5.dp.toPx()
            drawCircle(
                brush = haloGradient,
                style = Stroke(width = strokeWidth),
            )
        }
    } else if (isBackedUp) {
        // §9: small Aurora dot, bottom-right, once confirmed backed up
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Aurora, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Backed up",
                    tint = Color.Black,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}
