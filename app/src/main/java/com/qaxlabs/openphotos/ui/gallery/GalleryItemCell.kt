package com.qaxlabs.openphotos.ui.gallery

import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.qaxlabs.openphotos.ui.components.SyncHalo
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Panel

/**
 * Vault Gallery Grid Item (design_system.md §5 & §9).
 *
 * Chromeless 2dp corner tile with shared element transition support.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryItemCell(
    indexId: String,
    uriString: String,
    displayName: String,
    isVideo: Boolean,
    isUploading: Boolean,
    isBackedUp: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val imageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "photo_$indexId"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(Panel)
            .clickable(onClick = onClick),
    ) {
        // ── Thumbnail ──────────────────────────────────────────────────────────
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(Uri.parse(uriString))
                .crossfade(true)
                .size(240)
                .build(),
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(imageModifier),
            onState = { imageState = it },
        )

        // Error placeholder (if URI inaccessible)
        if (imageState is AsyncImagePainter.State.Error) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Panel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ImageNotSupported,
                    contentDescription = "Unavailable",
                    tint = Mist,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // ── Video badge (bottom-left) ──────────────────────────────────────────
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        // ── Sync Halo / Backed up indicator ─────────────────────────────────
        SyncHalo(
            isUploading = isUploading,
            isBackedUp = isBackedUp,
        )
    }
}
