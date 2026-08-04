package com.qaxlabs.openphotos.ui.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qaxlabs.openphotos.ui.components.BottomNavCapsule
import com.qaxlabs.openphotos.ui.components.GlassBox
import com.qaxlabs.openphotos.ui.components.GlassCard
import com.qaxlabs.openphotos.ui.components.NavDestination
import com.qaxlabs.openphotos.ui.theme.Ink
import com.qaxlabs.openphotos.ui.theme.Panel
import com.qaxlabs.openphotos.ui.theme.Signal
import com.qaxlabs.openphotos.ui.util.formatFileSize

/**
 * Device media browser screen (FR-MEDIA-2 + FR-MEDIA-3 & §10.5).
 *
 * Layout:
 *  - Void background.
 *  - 3-column LazyVerticalGrid of [MediaGridItem] cells, newest first.
 *  - Floating glass capsule at bottom when selection is active:
 *    "N items · X MB  ·  [Back up]"  (FR-MEDIA-3)
 *  - Floating glass bottom nav capsule when no items are selected.
 */
@Composable
fun MediaScreen(
    onNavigateDestination: (NavDestination) -> Unit,
    onNavigateToUpload: () -> Unit,
    vm: MediaViewModel = hiltViewModel(),
) {
    val context      = LocalContext.current
    val mediaItems   by vm.mediaItems.collectAsStateWithLifecycle()
    val isLoading    by vm.isLoading.collectAsStateWithLifecycle()
    val selectedIds  by vm.selectedIds.collectAsStateWithLifecycle()

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermission by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.all { it }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) vm.loadMedia()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        if (!hasPermission) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassCard {
                    Text(
                        text  = "Photo access needed",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = "Open Photos needs read access to your photos and videos " +
                                "so you can select which ones to back up. " +
                                "No files are uploaded without your explicit confirmation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick  = { permissionLauncher.launch(requiredPermissions) },
                        shape    = MaterialTheme.shapes.extraLarge,
                        colors   = ButtonDefaults.buttonColors(containerColor = Signal),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("Grant permission", color = Ink)
                    }
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val hasAnySelection = selectedIds.isNotEmpty()

            LazyVerticalGrid(
                columns               = GridCells.Fixed(3),
                contentPadding        = PaddingValues(
                    start  = 2.dp,
                    top    = 2.dp,
                    end    = 2.dp,
                    bottom = 90.dp  // leave room for capsule
                ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement   = Arrangement.spacedBy(2.dp),
                modifier              = Modifier.fillMaxSize(),
            ) {
                items(mediaItems, key = { it.id }) { item ->
                    MediaGridItem(
                        item           = item,
                        isSelected     = item.id in selectedIds,
                        hasAnySelection = hasAnySelection,
                        onToggle       = { vm.toggleSelection(item.id) },
                    )
                }
            }

            if (mediaItems.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = "No photos or videos found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Selection capsule (FR-MEDIA-3)
            AnimatedVisibility(
                visible = hasAnySelection,
                enter   = slideInVertically(animationSpec = tween(200), initialOffsetY = { it }),
                exit    = slideOutVertically(animationSpec = tween(150), targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    GlassBox(
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text  = "${vm.selectionCount} item${if (vm.selectionCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Ink,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text  = vm.selectionSizeBytes.formatFileSize(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ink.copy(alpha = 0.65f),
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            Button(
                                onClick = onNavigateToUpload,
                                shape  = MaterialTheme.shapes.extraLarge,
                                colors = ButtonDefaults.buttonColors(containerColor = Signal),
                            ) {
                                Text("Back up", color = Ink, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Floating glass bottom nav capsule when no items are selected
            AnimatedVisibility(
                visible = !hasAnySelection,
                enter   = fadeIn(),
                exit    = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomNavCapsule(
                    currentDestination = NavDestination.BACKUP,
                    onNavigate = onNavigateDestination,
                )
            }
        }
    }
}
