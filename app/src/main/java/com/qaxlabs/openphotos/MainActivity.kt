package com.qaxlabs.openphotos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qaxlabs.openphotos.ui.AppNavGraph
import com.qaxlabs.openphotos.ui.theme.OpenPhotosTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host.
 *
 * @AndroidEntryPoint enables Hilt injection into this Activity and any
 * Composable that uses [hiltViewModel] within its content tree.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenPhotosTheme {
                AppNavGraph()
            }
        }
    }
}