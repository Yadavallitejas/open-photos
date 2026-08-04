package com.qaxlabs.openphotos.ui

import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qaxlabs.openphotos.data.AuthState
import com.qaxlabs.openphotos.ui.auth.ApiCredentialsScreen
import com.qaxlabs.openphotos.ui.auth.AuthViewModel
import com.qaxlabs.openphotos.ui.auth.OtpScreen
import com.qaxlabs.openphotos.ui.auth.PasswordScreen
import com.qaxlabs.openphotos.ui.auth.PhoneScreen
import com.qaxlabs.openphotos.ui.auth.SplashScreen
import com.qaxlabs.openphotos.ui.components.NavDestination
import com.qaxlabs.openphotos.ui.gallery.GalleryDetailScreen
import com.qaxlabs.openphotos.ui.gallery.GalleryScreen
import com.qaxlabs.openphotos.ui.media.MediaScreen
import com.qaxlabs.openphotos.ui.media.MediaViewModel
import com.qaxlabs.openphotos.ui.settings.SettingsScreen
import com.qaxlabs.openphotos.ui.upload.UploadScreen
import com.qaxlabs.openphotos.ui.upload.UploadViewModel

private object Routes {
    const val SPLASH         = "splash"
    const val CREDENTIALS    = "api_credentials"
    const val PHONE          = "phone"
    const val OTP            = "otp"
    const val PASSWORD       = "password"
    const val GALLERY        = "gallery"
    const val MEDIA_PICKER   = "media_picker"
    const val SETTINGS       = "settings"
    const val UPLOAD_QUEUE   = "upload_queue"
    const val GALLERY_DETAIL = "gallery_detail/{indexId}"

    fun galleryDetail(indexId: String) = "gallery_detail/${Uri.encode(indexId)}"
}

/**
 * Root Navigation Graph (design_system.md §10).
 *
 * Auth flow: Splash -> Credentials -> Phone -> OTP -> Password -> Vault (Gallery)
 * Main destinations: Vault (Gallery), Backup (Media Picker), Settings, Upload Queue, Detail Viewer
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    val authVm: AuthViewModel = hiltViewModel()
    val authState by authVm.authState.collectAsStateWithLifecycle()

    // Data-driven Auth Navigation
    LaunchedEffect(authState) {
        val dest = when (authState) {
            is AuthState.Initializing       -> return@LaunchedEffect
            is AuthState.WaitingCredentials -> Routes.CREDENTIALS
            is AuthState.WaitingPhoneNumber -> Routes.PHONE
            is AuthState.WaitingCode        -> Routes.OTP
            is AuthState.WaitingPassword    -> Routes.PASSWORD
            is AuthState.Authenticated      -> Routes.GALLERY
            is AuthState.Error              -> return@LaunchedEffect
        }
        navController.navigate(dest) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
        ) {
            // ── Auth Flow ────────────────────────────────────────────────────────
            composable(Routes.SPLASH) { SplashScreen() }
            composable(Routes.CREDENTIALS) { ApiCredentialsScreen(authVm) }
            composable(Routes.PHONE) { PhoneScreen(authVm) }
            composable(Routes.OTP) { OtpScreen(authVm) }
            composable(Routes.PASSWORD) { PasswordScreen(authVm) }

            // ── Vault (Main Gallery Grid) ───────────────────────────────────────
            composable(Routes.GALLERY) {
                GalleryScreen(
                    onNavigateDestination = { dest ->
                        when (dest) {
                            NavDestination.VAULT -> {}
                            NavDestination.BACKUP -> navController.navigate(Routes.MEDIA_PICKER) {
                                launchSingleTop = true
                            }
                            NavDestination.SETTINGS -> navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onItemClick = { indexId ->
                        navController.navigate(Routes.galleryDetail(indexId))
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                )
            }

            // ── Backup (Device Media Picker) ────────────────────────────────────
            composable(Routes.MEDIA_PICKER) {
                val mediaVm: MediaViewModel = hiltViewModel()
                val uploadVm: UploadViewModel = hiltViewModel()
                MediaScreen(
                    onNavigateDestination = { dest ->
                        when (dest) {
                            NavDestination.VAULT -> navController.navigate(Routes.GALLERY) {
                                popUpTo(Routes.GALLERY) { inclusive = true }
                                launchSingleTop = true
                            }
                            NavDestination.BACKUP -> {}
                            NavDestination.SETTINGS -> navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onNavigateToUpload = {
                        uploadVm.startUpload(mediaVm.selectedItems)
                        mediaVm.clearSelection()
                        navController.navigate(Routes.UPLOAD_QUEUE)
                    },
                    vm = mediaVm,
                )
            }

            // ── Upload Status Queue ─────────────────────────────────────────────
            composable(Routes.UPLOAD_QUEUE) {
                UploadScreen(
                    onDone = {
                        navController.navigate(Routes.GALLERY) {
                            popUpTo(Routes.GALLERY) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            // ── Settings ────────────────────────────────────────────────────────
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateDestination = { dest ->
                        when (dest) {
                            NavDestination.VAULT -> navController.navigate(Routes.GALLERY) {
                                popUpTo(Routes.GALLERY) { inclusive = true }
                                launchSingleTop = true
                            }
                            NavDestination.BACKUP -> navController.navigate(Routes.MEDIA_PICKER) {
                                launchSingleTop = true
                            }
                            NavDestination.SETTINGS -> {}
                        }
                    },
                    authVm = authVm,
                )
            }

            // ── Full-screen Detail Viewer ───────────────────────────────────────
            composable(
                route = Routes.GALLERY_DETAIL,
                arguments = listOf(
                    navArgument("indexId") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val indexId = backStackEntry.arguments?.getString("indexId") ?: return@composable
                GalleryDetailScreen(
                    initialIndexId = indexId,
                    onBack = { navController.popBackStack() },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                )
            }
        }
    }
}
