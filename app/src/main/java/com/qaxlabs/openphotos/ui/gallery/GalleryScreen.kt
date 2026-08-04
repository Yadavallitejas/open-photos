package com.qaxlabs.openphotos.ui.gallery

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qaxlabs.openphotos.ui.components.BottomNavCapsule
import com.qaxlabs.openphotos.ui.components.GlassBox
import com.qaxlabs.openphotos.ui.components.NavDestination
import com.qaxlabs.openphotos.ui.theme.Ink
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Void

/**
 * Vault Gallery Screen (design_system.md §10.3).
 *
 * Full-bleed 3-column grid on Void background (#0A0B10).
 * Floating glass top bar + floating glass bottom navigation capsule (§9).
 * Chromeless grid tiles (2dp corner radius, 2dp gutters).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    onNavigateDestination: (NavDestination) -> Unit,
    onItemClick: (indexId: String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    vm: GalleryViewModel = hiltViewModel(),
) {
    val items by vm.combinedItems.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // ── Chromeless 3-column grid / Empty State ───────────────────────────
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 90.dp, bottom = 90.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = Mist,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "Your vault is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Back up some photos to populate your vault.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mist,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    top = 84.dp,
                    bottom = 90.dp,
                    start = 2.dp,
                    end = 2.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = items,
                    key = { it.indexId },
                ) { item ->
                    GalleryItemCell(
                        indexId = item.indexId,
                        uriString = item.localUri,
                        displayName = item.displayName,
                        isVideo = item.isVideo,
                        isUploading = item.isUploading,
                        isBackedUp = item.isBackedUp,
                        onClick = { onItemClick(item.indexId) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }

        // ── Floating Glass Top Bar ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            GlassBox(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Vault",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                    )

                    if (items.isNotEmpty()) {
                        Text(
                            text = "${items.size} item${if (items.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist,
                        )
                    }
                }
            }
        }

        // ── Floating Glass Bottom Navigation Capsule ────────────────────────
        BottomNavCapsule(
            currentDestination = NavDestination.VAULT,
            onNavigate = onNavigateDestination,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
