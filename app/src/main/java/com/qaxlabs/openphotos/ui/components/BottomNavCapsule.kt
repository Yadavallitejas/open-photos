package com.qaxlabs.openphotos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Signal

enum class NavDestination {
    VAULT, BACKUP, SETTINGS
}

/**
 * Floating Glass Bottom Navigation Capsule (design_system.md §9).
 *
 * Inset 12dp from screen bottom and side edges (not edge-to-edge).
 * 3 destinations: Vault, Backup, Settings.
 * Active item tinted Signal (#6E5BFF), inactive items tinted Mist (#8B90A3).
 */
@Composable
fun BottomNavCapsule(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassBox(
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.height(60.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(
                    label = "Vault",
                    selectedIcon = Icons.Filled.GridView,
                    unselectedIcon = Icons.Outlined.GridView,
                    isSelected = currentDestination == NavDestination.VAULT,
                    onClick = { onNavigate(NavDestination.VAULT) },
                )

                NavItem(
                    label = "Backup",
                    selectedIcon = Icons.Filled.CloudUpload,
                    unselectedIcon = Icons.Outlined.CloudUpload,
                    isSelected = currentDestination == NavDestination.BACKUP,
                    onClick = { onNavigate(NavDestination.BACKUP) },
                )

                NavItem(
                    label = "Settings",
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    isSelected = currentDestination == NavDestination.SETTINGS,
                    onClick = { onNavigate(NavDestination.SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (isSelected) Signal else Mist

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = tint,
        )
    }
}
