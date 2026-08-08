package com.qaxlabs.openphotos.ui.gallery

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.qaxlabs.openphotos.data.ViewerFileState
import com.qaxlabs.openphotos.ui.components.GlassBox
import com.qaxlabs.openphotos.ui.theme.DataTextStyle
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Signal
import com.qaxlabs.openphotos.ui.util.formatFileSize
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MAX_ZOOM = 5f
private const val MIN_ZOOM = 1f

/**
 * Full-screen Vault Viewer (design_system.md §10.4).
 *
 * - Pure black background.
 * - Swipeable HorizontalPager.
 * - Full-resolution fetch on page settle (FR-GALLERY-3).
 * - Pinch-to-zoom + double-tap-to-zoom on images (FR-GALLERY-4).
 * - Explicit Download action -> MediaStore save (FR-GALLERY-5).
 * - Video: download-then-play via external intent (v1 limitation).
 * - Glass control bars top and bottom, fade on tap.
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
    val viewerStateMap by vm.viewerStateMap.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Snackbar host
    val snackbarHostState = remember { SnackbarHostState() }

    // Consume download results
    LaunchedEffect(Unit) {
        vm.downloadResult.collect { result ->
            when (result) {
                is DownloadResult.Success ->
                    snackbarHostState.showSnackbar("Saved to gallery ✓")
                is DownloadResult.Failure ->
                    snackbarHostState.showSnackbar("Download failed: ${result.reason}")
            }
        }
    }

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

    // Auto-hide controls after 3 s
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // Fetch full-res when pager settles on a new page
    LaunchedEffect(pagerState.currentPage) {
        if (!pagerState.isScrollInProgress) {
            items.getOrNull(pagerState.currentPage)?.let { item ->
                if (item.entity != null) {
                    vm.fetchFullResolution(item.indexId)
                }
            }
        }
    }
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            items.getOrNull(pagerState.currentPage)?.let { item ->
                if (item.entity != null) {
                    vm.fetchFullResolution(item.indexId)
                }
            }
        }
    }

    val currentViewerState = currentItem?.indexId?.let { viewerStateMap[it] } ?: ViewerFileState.Idle

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.systemBars)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { controlsVisible = !controlsVisible },
                ),
        ) {
            // ── Swipeable Full-resolution Pager ──────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { items.getOrNull(it)?.indexId ?: it },
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
                val fileState = item.indexId.let { viewerStateMap[it] } ?: ViewerFileState.Idle
                if (item.isVideo) {
                    VideoPage(
                        item = item,
                        fileState = fileState,
                        onPlayRequested = { localPath ->
                            // Launch external video player
                            val file = File(localPath)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, item.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                        },
                    )
                } else {
                    ImagePage(
                        item = item,
                        fileState = fileState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onTap = { controlsVisible = !controlsVisible },
                    )
                }
            }

            // ── Download loading overlay (shown while full-res is loading) ───
            if (currentViewerState is ViewerFileState.Loading) {
                LinearProgressIndicator(
                    progress = { currentViewerState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp),
                    color = Signal,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }

            // ── Floating Glass Top Control Bar ───────────────────────────────
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

            // ── Floating Glass Bottom Metadata + Download Bar ────────────────
            AnimatedVisibility(
                visible = controlsVisible && currentItem != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                currentItem?.let { item ->
                    val itemViewerState = viewerStateMap[item.indexId] ?: ViewerFileState.Idle
                    DetailBottomBar(
                        item = item,
                        viewerState = itemViewerState,
                        onDownload = { vm.downloadToGallery(item.indexId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

// ── Image page with pinch + double-tap zoom ───────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ImagePage(
    item: VaultGalleryItem,
    fileState: ViewerFileState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    // Resolve the best image source available
    val imageSource = remember(fileState, item.thumbnailPath, item.localUri) {
        when {
            fileState is ViewerFileState.Ready -> File(fileState.localPath)
            item.localUri.startsWith("file://") -> {
                val f = File(item.localUri.removePrefix("file://"))
                if (f.exists() && f.length() > 0) f
                else item.thumbnailPath?.let { File(it) } ?: Uri.parse(item.localUri)
            }
            !item.thumbnailPath.isNullOrEmpty() -> File(item.thumbnailPath)
            else -> Uri.parse(item.localUri)
        }
    }

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "photo_${item.indexId}"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Pinch-to-zoom + pan + double-tap
                var lastDoubleTapTime = 0L
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var pointersDown = 1

                    // Detect double tap for zoom toggle
                    val now = System.currentTimeMillis()
                    val isDoubleTap = (now - lastDoubleTapTime) < 300L
                    lastDoubleTapTime = now

                    if (isDoubleTap) {
                        // Toggle between 1x and 2.5x
                        scale = if (scale > 1.5f) 1f else 2.5f
                        offset = Offset.Zero
                        firstDown.consume()
                        return@awaitEachGesture
                    }

                    do {
                        val event = awaitPointerEvent()
                        pointersDown = event.changes.count { it.pressed }

                        if (pointersDown >= 2) {
                            // Pinch gesture
                            val zoomFactor = event.calculateZoom()
                            val pan = event.calculatePan()

                            val newScale = (scale * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            scale = newScale

                            if (newScale > 1f) {
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y,
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pointersDown == 1 && scale > 1f) {
                            // Pan when zoomed in
                            val pan = event.calculatePan()
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = offset.y + pan.y,
                            )
                            event.changes.forEach { it.consume() }
                        }
                    } while (pointersDown > 0 &&
                        event.changes.any { it.pressed })

                    // Snap back if under minimum zoom
                    if (scale < MIN_ZOOM + 0.05f) {
                        scale = MIN_ZOOM
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageSource)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .then(sharedModifier),
            onState = { imageState = it },
        )

        // Error placeholder
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
                    text = "Preview unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }

        // Loading shimmer while full-res is still fetching
        if (fileState is ViewerFileState.Loading && imageState !is AsyncImagePainter.State.Error) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Loading full resolution…",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Video page: shows thumbnail + Play button, tapping fetches & opens player ──

@Composable
private fun VideoPage(
    item: VaultGalleryItem,
    fileState: ViewerFileState,
    onPlayRequested: (localPath: String) -> Unit,
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val thumbnailSource = remember(item.thumbnailPath, item.localUri) {
        when {
            !item.thumbnailPath.isNullOrEmpty() -> File(item.thumbnailPath)
            item.localUri.startsWith("file://") -> File(item.localUri.removePrefix("file://"))
            else -> Uri.parse(item.localUri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // Blurred thumbnail as background
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailSource)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.4f },
            onState = { imageState = it },
        )

        when (fileState) {
            is ViewerFileState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(
                        color = Signal,
                        modifier = Modifier.size(56.dp),
                    )
                    if (fileState.progress > 0f) {
                        Text(
                            text = "${(fileState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }
                }
            }
            is ViewerFileState.Ready -> {
                IconButton(
                    onClick = { onPlayRequested(fileState.localPath) },
                    modifier = Modifier.size(80.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(80.dp),
                    )
                }
            }
            is ViewerFileState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ImageNotSupported,
                        contentDescription = null,
                        tint = Mist,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        text = fileState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                    )
                }
            }
            else -> {
                // Idle: show a static play icon as placeholder
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(72.dp),
                )
            }
        }
    }
}

// ── Bottom glass bar: metadata + Download button ───────────────────────────────

private val dateFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())

@Composable
private fun DetailBottomBar(
    item: VaultGalleryItem,
    viewerState: ViewerFileState,
    onDownload: () -> Unit,
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

    val canDownload = viewerState is ViewerFileState.Ready && item.entity != null
    val isDownloading = viewerState is ViewerFileState.Loading

    GlassBox(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = takenDate,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                Text(
                    text = "${item.mimeType}  •  ${item.sizeBytes.formatFileSize()}",
                    style = DataTextStyle,
                    color = Mist,
                )
            }

            // Download button — only enabled when full-res is ready
            IconButton(
                onClick = onDownload,
                enabled = canDownload,
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        color = Signal,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download to device gallery",
                        tint = if (canDownload) Color.White else Mist,
                    )
                }
            }
        }
    }
}
