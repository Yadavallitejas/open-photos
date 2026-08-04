package com.qaxlabs.openphotos.ui.gallery

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.qaxlabs.openphotos.ui.components.GlassBox
import com.qaxlabs.openphotos.ui.theme.DataTextStyle
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Signal
import com.qaxlabs.openphotos.ui.util.formatFileSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Full-screen Vault Viewer (design_system.md §10.4).
 *
 * Pure black background (#000000). Image/video fills frame.
 * Glass control bar top and bottom fade in/out on tap.
 * Swipe left/right between items via HorizontalPager.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryDetailScreen(
    initialIndexId: String,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    vm: GalleryViewModel = hiltViewModel(),
) {
    val items by vm.combinedItems.collectAsStateWithLifecycle()

    val initialPage = remember(items, initialIndexId) {
        items.indexOfFirst { it.indexId == initialIndexId }.coerceAtLeast(0)
    }

    if (items.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Signal)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { items.size }
    val currentItem = items.getOrNull(pagerState.currentPage)
    var controlsVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { controlsVisible = !controlsVisible },
            ),
    ) {
        // ── Swipeable Full-resolution Pager ─────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            ItemFullPage(
                item = item,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }

        // ── Floating Glass Top Control Bar ──────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                GlassBox(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                        Text(
                            text = currentItem?.displayName ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${pagerState.currentPage + 1} / ${items.size}",
                            style = DataTextStyle,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }

        // ── Floating Glass Bottom Metadata Bar ───────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible && currentItem != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            currentItem?.let { item ->
                DetailMetadataBar(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ItemFullPage(
    item: VaultGalleryItem,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val imageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "photo_${item.indexId}"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(Uri.parse(item.localUri))
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .then(imageModifier),
            onState = { imageState = it },
        )

        if (imageState is AsyncImagePainter.State.Error) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ImageNotSupported,
                    contentDescription = null,
                    tint = Mist,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = "File content unavailable locally",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }

        if (item.isVideo && imageState !is AsyncImagePainter.State.Error) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

private val dateFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())

@Composable
private fun DetailMetadataBar(
    item: VaultGalleryItem,
    modifier: Modifier = Modifier,
) {
    val takenDate = remember(item.entity?.takenAt) {
        val millis = item.entity?.takenAt ?: 0L
        if (millis > 0) {
            runCatching {
                dateFormatter.format(Instant.ofEpochMilli(millis))
            }.getOrDefault("—")
        } else "—"
    }

    GlassBox(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = takenDate,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                Text(
                    text = item.mimeType,
                    style = DataTextStyle,
                    color = Mist,
                )
            }
            Text(
                text = item.sizeBytes.formatFileSize(),
                style = DataTextStyle,
                color = Color.White,
            )
        }
    }
}
