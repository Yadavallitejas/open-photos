package com.qaxlabs.openphotos.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qaxlabs.openphotos.ui.auth.AuthViewModel
import com.qaxlabs.openphotos.ui.components.BottomNavCapsule
import com.qaxlabs.openphotos.ui.components.GlassBox
import com.qaxlabs.openphotos.ui.components.NavDestination
import com.qaxlabs.openphotos.ui.theme.Aurora
import com.qaxlabs.openphotos.ui.theme.DataTextStyle
import com.qaxlabs.openphotos.ui.theme.Ember
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Signal
import com.qaxlabs.openphotos.ui.theme.Void
import com.qaxlabs.openphotos.ui.util.formatFileSize

/**
 * Settings Screen (design_system.md §10.7 & FR-SETTINGS).
 *
 * Grouped sections:
 *  - Account: Telegram Session, View/Edit stored api_id & api_hash (FR-SETTINGS-1).
 *  - Auto-Backup: WorkManager periodic sync toggle (NG3).
 *  - Storage & Vault: Saved Messages location, Total items backed up, Total size (FR-SETTINGS-3).
 *  - About: Version, GitHub repository link, Apache 2.0 license link (FR-SETTINGS-4).
 *  - Log out of Telegram (FR-SETTINGS-2).
 */
@Composable
fun SettingsScreen(
    onNavigateDestination: (NavDestination) -> Unit,
    authVm: AuthViewModel = hiltViewModel(),
    backupVm: BackupSettingsViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditCredentialsDialog by remember { mutableStateOf(false) }

    val autoBackupEnabled by backupVm.autoBackupEnabled.collectAsStateWithLifecycle()
    val apiId by settingsVm.apiId.collectAsStateWithLifecycle()
    val apiHash by settingsVm.apiHash.collectAsStateWithLifecycle()
    val backedUpCount by settingsVm.backedUpCount.collectAsStateWithLifecycle()
    val totalSizeBytes by settingsVm.totalSizeBytes.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 90.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Screen title
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            // Account section
            SettingsSection(title = "Account") {
                SettingsRow(
                    icon = Icons.Filled.Phone,
                    label = "Telegram Session",
                    value = "Connected",
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEditCredentialsDialog = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = Mist,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(
                                text = "API Credentials",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (apiId != null && apiHash != null)
                                    "ID: $apiId | Hash: ${apiHash?.take(4)}****"
                                else "Tap to edit api_id / api_hash",
                                style = DataTextStyle,
                                color = Mist,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit API credentials",
                        tint = Signal,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Auto-backup section
            SettingsSection(title = "Auto-Backup") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            tint = Mist,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(
                                text = "Automatically back up",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Checks every 15 min when connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mist,
                            )
                        }
                    }
                    Switch(
                        checked = autoBackupEnabled,
                        onCheckedChange = { backupVm.setAutoBackupEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Aurora,
                            checkedTrackColor = Aurora.copy(alpha = 0.3f),
                        ),
                    )
                }
            }

            // Storage section (FR-SETTINGS-3)
            SettingsSection(title = "Storage & Vault") {
                SettingsRow(
                    icon = Icons.Filled.Cloud,
                    label = "Storage Location",
                    value = "Saved Messages",
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingsRow(
                    icon = Icons.Filled.PermMedia,
                    label = "Backed Up Items",
                    value = "$backedUpCount items",
                    isDataText = true,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingsRow(
                    icon = Icons.Filled.Cloud,
                    label = "Total Storage Used",
                    value = totalSizeBytes.formatFileSize(),
                    isDataText = true,
                )
            }

            // About section (FR-SETTINGS-4)
            SettingsSection(title = "About & License") {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    label = "OpenPhotos Version",
                    value = "v1.0.0-alpha",
                    isDataText = true,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yadavallitejas/open-photos"))
                            context.startActivity(intent)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = Mist,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "GitHub Repository",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "open-photos",
                        style = DataTextStyle,
                        color = Signal,
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0"))
                            context.startActivity(intent)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = Mist,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "License",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "Apache 2.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Logout action (FR-SETTINGS-2)
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Ember,
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).let {
                    androidx.compose.foundation.BorderStroke(1.dp, Ember.copy(alpha = 0.5f))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Log out",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Log out of Telegram",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ── Floating Glass Bottom Navigation Capsule ────────────────────────
        BottomNavCapsule(
            currentDestination = NavDestination.SETTINGS,
            onNavigate = onNavigateDestination,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ── Edit Credentials Dialog ─────────────────────────────────────────────
    if (showEditCredentialsDialog) {
        EditCredentialsDialog(
            currentApiId = apiId,
            currentApiHash = apiHash,
            onDismiss = { showEditCredentialsDialog = false },
            onSave = { newId, newHash ->
                settingsVm.updateCredentials(newId, newHash)
                showEditCredentialsDialog = false
            }
        )
    }

    // ── Logout Confirmation Dialog ──────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Log out of OpenPhotos?",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = "Your TDLib session and local credentials will be cleared. Photos stored in Telegram Saved Messages remain intact.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        authVm.logout()
                    }
                ) {
                    Text("Log out", color = Ember)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = Mist)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun EditCredentialsDialog(
    currentApiId: Int?,
    currentApiHash: String?,
    onDismiss: () -> Unit,
    onSave: (apiId: Int, apiHash: String) -> Unit,
) {
    var idText by remember { mutableStateOf(currentApiId?.toString() ?: "") }
    var hashText by remember { mutableStateOf(currentApiHash ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit API Credentials",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter your Telegram api_id and api_hash from my.telegram.org.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
                OutlinedTextField(
                    value = idText,
                    onValueChange = { idText = it },
                    label = { Text("api_id") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Signal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hashText,
                    onValueChange = { hashText = it },
                    label = { Text("api_hash") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Signal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = Ember,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedId = idText.trim().toIntOrNull()
                    val trimmedHash = hashText.trim()
                    if (parsedId == null || parsedId <= 0) {
                        errorMessage = "Invalid api_id (numeric required)"
                    } else if (trimmedHash.isBlank()) {
                        errorMessage = "api_hash cannot be empty"
                    } else {
                        onSave(parsedId, trimmedHash)
                    }
                }
            ) {
                Text("Save", color = Signal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Mist)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
        GlassBox(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    isDataText: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Mist,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = value,
            style = if (isDataText) DataTextStyle else MaterialTheme.typography.bodySmall,
            color = Mist,
        )
    }
}
