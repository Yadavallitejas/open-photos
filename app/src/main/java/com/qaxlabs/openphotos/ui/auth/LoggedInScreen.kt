package com.qaxlabs.openphotos.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qaxlabs.openphotos.ui.components.GlassCard
import com.qaxlabs.openphotos.ui.theme.Signal

/**
 * Success screen — shown after [AuthState.Authenticated].
 *
 * Meets the success criterion: "Logged in as +<phone number>"
 * (design_system.md §10 — glass-card-on-Void pattern).
 */
@Composable
fun LoggedInScreen(phoneNumber: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard {
            Text(
                text  = "Logged in as",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text  = phoneNumber.ifBlank { "—" },
                style = MaterialTheme.typography.displayLarge,
                color = Signal,      // Signal accent — the single CTA token per screen
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text  = "Your vault is ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
