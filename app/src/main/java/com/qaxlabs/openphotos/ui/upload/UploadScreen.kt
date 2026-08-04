package com.qaxlabs.openphotos.ui.upload

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.qaxlabs.openphotos.data.FloodWaitState
import com.qaxlabs.openphotos.data.UploadQueueItem
import com.qaxlabs.openphotos.data.UploadState
import com.qaxlabs.openphotos.ui.theme.Aurora
import com.qaxlabs.openphotos.ui.theme.Ember
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Signal
import com.qaxlabs.openphotos.ui.util.formatFileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil

/**
 * Upload progress screen (FR-UPLOAD-3).
 *
 * Shows the full upload queue with a per-row state chip for each item:
 *   Queued      → clock icon + "Queued" (Mist)
 *   Uploading   → animated LinearProgressIndicator (Signal)
 *   Done        → Aurora checkmark + "Backed up"
 *   Failed      → Ember dot + error reason
 *   Oversized   → Ember badge + "Too large (>2 GB)"
 *
 * Back navigation is blocked while any upload is active to prevent the user
 * from accidentally abandoning an in-progress transfer.
 *
 * [onDone] is invoked when all uploads complete and the user taps "Done".
 */
@Composable
fun UploadScreen(
    onDone: () -> Unit,
    vm: UploadViewModel = hiltViewModel(),
) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val floodWaitState by vm.floodWaitState.collectAsStateWithLifecycle()

    val doneCount      = remember(queue) { queue.count { it.state is UploadState.Done } }
    val totalCount     = remember(queue) { queue.size }
    val hasActive      = remember(queue) { queue.any { it.state is UploadState.Uploading } }
    val isAllFinished  = remember(queue) {
        queue.isNotEmpty() && queue.none { it.state is UploadState.Queued || it.state is UploadState.Uploading }
    }

    // Block back-press during active uploads
    BackHandler(enabled = hasActive) { /* swallow — don't navigate away */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // ── Flood-wait banner (FR-BACKUP-STATUS-2) ────────────────────────────
        FloodWaitBanner(floodWaitState)

        // ── Header ────────────────────────────────────────────────────────────
        UploadHeader(
            doneCount     = doneCount,
            totalCount    = totalCount,
            isAllFinished = isAllFinished,
            onDone        = {
                vm.clearQueue()
                onDone()
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // ── Per-file list ─────────────────────────────────────────────────────
        LazyColumn(
            contentPadding        = PaddingValues(vertical = 8.dp),
            verticalArrangement   = Arrangement.spacedBy(2.dp),
            modifier              = Modifier.fillMaxSize(),
        ) {
            items(queue, key = { it.id }) { item ->
                UploadItemRow(
                    item = item,
                    onRetry = { vm.retryUpload(item.id) },
                )
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun UploadHeader(
    doneCount: Int,
    totalCount: Int,
    isAllFinished: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "Backing up to Saved Messages",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "$doneCount of $totalCount backed up",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isAllFinished) {
                Button(
                    onClick  = onDone,
                    shape    = MaterialTheme.shapes.extraLarge,
                    colors   = ButtonDefaults.buttonColors(containerColor = Signal),
                ) {
                    Text("Done", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                }
            } else {
                // Indeterminate indicator while uploads are active
                CircularProgressIndicator(
                    modifier    = Modifier.size(24.dp),
                    color       = Signal,
                    trackColor  = MaterialTheme.colorScheme.surface,
                    strokeWidth = 2.dp,
                )
            }
        }

        // Overall progress bar
        if (!isAllFinished && totalCount > 0) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress  = { doneCount.toFloat() / totalCount },
                color     = Signal,
                trackColor = MaterialTheme.colorScheme.surface,
                modifier  = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

// ── Per-item row ──────────────────────────────────────────────────────────────

@Composable
private fun UploadItemRow(
    item: UploadQueueItem,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        AsyncImage(
            model              = item.mediaItem.uri,
            contentDescription = item.mediaItem.displayName,
            modifier           = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
        )

        Spacer(Modifier.width(12.dp))

        // Filename + size + error message
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text      = item.mediaItem.displayName,
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurface,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
            )
            Text(
                text  = item.mediaItem.sizeBytes.formatFileSize(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Progress bar — only visible while uploading
            if (item.state is UploadState.Uploading) {
                Spacer(Modifier.height(4.dp))
                val uploading = item.state as UploadState.Uploading
                if (uploading.isIndeterminate) {
                    LinearProgressIndicator(
                        color     = Signal,
                        trackColor = MaterialTheme.colorScheme.surface,
                        modifier  = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        progress  = { uploading.fraction },
                        color     = Signal,
                        trackColor = MaterialTheme.colorScheme.surface,
                        modifier  = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text  = "${uploading.uploadedBytes.formatFileSize()} / ${uploading.totalBytes.formatFileSize()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mist,
                    )
                }
            }

            // Error details — visible if failed
            if (item.state is UploadState.Failed) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = (item.state as UploadState.Failed).reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = Ember,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // State chip / retry action
        UploadStateChip(state = item.state, onRetry = onRetry)
    }
}

@Composable
private fun UploadStateChip(
    state: UploadState,
    onRetry: () -> Unit,
) {
    when (state) {
        is UploadState.Queued -> {
            Icon(
                imageVector        = Icons.Filled.Schedule,
                contentDescription = "Queued",
                tint               = Mist,
                modifier           = Modifier.size(20.dp),
            )
        }
        is UploadState.Uploading -> {
            // Progress is shown inline; chip is a small "uploading" dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Signal),
            )
        }
        is UploadState.Done -> {
            Icon(
                imageVector        = Icons.Filled.CheckCircle,
                contentDescription = "Done",
                tint               = Aurora,
                modifier           = Modifier.size(22.dp),
            )
        }
        is UploadState.Failed -> {
            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Refresh,
                    contentDescription = "Retry upload",
                    tint               = Ember,
                    modifier           = Modifier.size(22.dp),
                )
            }
        }
        is UploadState.Oversized -> {
            Icon(
                imageVector        = Icons.Filled.Warning,
                contentDescription = "Too large",
                tint               = Ember,
                modifier           = Modifier.size(22.dp),
            )
        }
    }
}

// ── Flood-wait banner ─────────────────────────────────────────────────────────

/**
 * Dismissible banner that appears when [FloodWaitState.Waiting] is active
 * (FR-BACKUP-STATUS-2). Shows a live countdown in minutes so the user knows
 * when uploads will resume automatically.
 *
 * Slides in/out via [AnimatedVisibility] to avoid jarring layout jumps.
 */
@Composable
private fun FloodWaitBanner(state: FloodWaitState) {
    AnimatedVisibility(
        visible = state is FloodWaitState.Waiting,
        enter   = expandVertically(),
        exit    = shrinkVertically(),
    ) {
        if (state is FloodWaitState.Waiting) {
            // Live countdown: recomputes every second until resumesAt is past.
            val remainingMins by produceState(initialValue = 0L, state) {
                while (isActive) {
                    val remainingSecs = (state.resumesAt - System.currentTimeMillis()) / 1_000L
                    value = ceil(remainingSecs / 60.0).toLong().coerceAtLeast(0L)
                    if (value == 0L) break
                    delay(1_000L)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ember.copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.HourglassBottom,
                    contentDescription = "Paused",
                    tint               = Ember,
                    modifier           = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Upload paused — Telegram rate limit",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ember,
                        fontWeight = FontWeight.Medium,
                    )
                    if (remainingMins > 0L) {
                        Text(
                            text  = "Resuming in ~$remainingMins min",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ember.copy(alpha = 0.7f),
                        )
                    } else {
                        Text(
                            text  = "Resuming shortly…",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ember.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
