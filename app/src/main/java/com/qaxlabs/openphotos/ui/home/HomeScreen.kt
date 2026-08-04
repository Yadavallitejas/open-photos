package com.qaxlabs.openphotos.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qaxlabs.openphotos.data.SyncState
import com.qaxlabs.openphotos.ui.components.GlassCard
import com.qaxlabs.openphotos.ui.theme.Aurora
import com.qaxlabs.openphotos.ui.theme.Ember
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Panel
import com.qaxlabs.openphotos.ui.theme.Signal

/**
 * Post-authentication home screen (hub).
 *
 * Surfaces three things:
 *  1. Auth confirmation ("Logged in as +<phone>").
 *  2. Index sync banner while restoring the vault after login (FR-INDEX-2).
 *  3. Two CTAs: "Browse photos" (opens device picker) and
 *     "View Vault (N)" (opens the backed-up gallery — FR-GALLERY-1).
 *
 * [syncState] is observed from [HomeViewModel.syncState].
 * [vaultItemCount] drives the count badge on "View Vault".
 */
@Composable
fun HomeScreen(
    phoneNumber: String,
    syncState: SyncState,
    vaultItemCount: Int,
    onBrowsePhotos: () -> Unit,
    onViewVault: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard {
            // ── Auth confirmation ─────────────────────────────────────────────
            Text(
                text  = "Logged in as",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text       = phoneNumber.ifBlank { "—" },
                style      = MaterialTheme.typography.displayLarge,
                color      = Signal,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(12.dp))

            // ── Index sync status (FR-INDEX-2) ────────────────────────────────
            AnimatedVisibility(
                visible = syncState !is SyncState.Idle,
                enter   = fadeIn(),
                exit    = fadeOut(),
            ) {
                Column {
                    when (syncState) {
                        is SyncState.Syncing -> {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector        = Icons.Filled.CloudDownload,
                                    contentDescription = null,
                                    tint               = Aurora,
                                    modifier           = Modifier.size(18.dp),
                                )
                                Text(
                                    text  = "Restoring your vault from Telegram…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Aurora,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                color      = Aurora,
                                trackColor = Aurora.copy(alpha = 0.15f),
                                modifier   = Modifier.fillMaxWidth(),
                            )
                        }

                        is SyncState.Done -> {
                            val count = syncState.itemCount
                            Text(
                                text  = if (count > 0)
                                    "$count item${if (count == 1) "" else "s"} restored from your vault."
                                else
                                    "Vault is up to date.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Mist,
                            )
                        }

                        is SyncState.Error -> {
                            Text(
                                text  = "Vault sync failed: ${syncState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ember,
                            )
                        }

                        else -> Unit
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (syncState is SyncState.Idle) {
                Text(
                    text  = "Your vault is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(20.dp))

            // ── Primary CTA: Browse photos ────────────────────────────────────
            Button(
                onClick  = onBrowsePhotos,
                shape    = MaterialTheme.shapes.extraLarge,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Signal,
                    contentColor   = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "Browse photos",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Secondary CTA: View Vault ─────────────────────────────────────
            OutlinedButton(
                onClick  = onViewVault,
                enabled  = vaultItemCount > 0,
                shape    = MaterialTheme.shapes.extraLarge,
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor         = Aurora,
                    disabledContentColor = Mist,
                ),
                border   = ButtonDefaults.outlinedButtonBorder(enabled = vaultItemCount > 0).let { border ->
                    if (vaultItemCount > 0)
                        androidx.compose.foundation.BorderStroke(1.dp, Aurora.copy(alpha = 0.5f))
                    else
                        androidx.compose.foundation.BorderStroke(1.dp, Mist.copy(alpha = 0.2f))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.GridView,
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = if (vaultItemCount > 0)
                        "View Vault ($vaultItemCount)"
                    else
                        "Vault empty",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
