package com.weekclip.android.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.weekclip.android.auth.AuthViewModel
import androidx.compose.ui.Modifier
import com.weekclip.android.auth.ui.LoginScreen
import com.weekclip.android.studio.ui.MediaListScreen
import com.weekclip.android.studio.ui.StudioListScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.weekclip.android.studio.ui.VideoPlayerScreen
import com.weekclip.android.upload.ui.UploadScreen

sealed class Route(val path: String) {
  data object Login : Route("login")
  data object StudioList : Route("studios")
  data class MediaList(val studioId: String) : Route("studios/$studioId/media")
  data class VideoPlayer(val studioId: String, val mediaId: String) : Route("studios/$studioId/media/$mediaId")

  companion object {
    fun mediaList(studioId: String) = "studios/$studioId/media"
    fun videoPlayer(studioId: String, mediaId: String) = "studios/$studioId/media/$mediaId"
  }
}

@Composable
fun NavigationHost(
  authViewModel: AuthViewModel = hiltViewModel(),
  startDestination: String = Route.StudioList.path
) {
  val navController = rememberNavController()

  Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
      navController = navController,
      startDestination = startDestination
    ) {
      composable(Route.Login.path) {
        LoginScreen(
          viewModel = authViewModel,
          onLoginSuccess = {
            navController.navigate(Route.StudioList.path) {
              popUpTo(Route.Login.path) { inclusive = true }
            }
          }
        )
      }

      composable(Route.StudioList.path) {
        StudioListScreen(
          onStudioSelected = { studioId ->
            navController.navigate(Route.mediaList(studioId))
          },
          onLogout = {
            navController.navigate(Route.Login.path) {
              popUpTo(Route.StudioList.path) { inclusive = true }
            }
          }
        )
      }

      composable(
        route = "studios/{studioId}/media",
        arguments = listOf(
          androidx.navigation.navArgument("studioId") {
            type = androidx.navigation.NavType.StringType
          }
        )
      ) { backStackEntry ->
        val studioId = backStackEntry.arguments?.getString("studioId") ?: return@composable

        MediaListScreen(
          studioId = studioId,
          onBack = { navController.popBackStack() },
          onMediaSelected = { mediaId ->
            navController.navigate(Route.videoPlayer(studioId, mediaId))
          }
        )
      }

      composable(
        route = "studios/{studioId}/media/{mediaId}",
        arguments = listOf(
          androidx.navigation.navArgument("studioId") {
            type = androidx.navigation.NavType.StringType
          },
          androidx.navigation.navArgument("mediaId") {
            type = androidx.navigation.NavType.StringType
          }
        )
      ) { backStackEntry ->
        val studioId = backStackEntry.arguments?.getString("studioId") ?: return@composable
        val mediaId = backStackEntry.arguments?.getString("mediaId") ?: return@composable

        VideoPlayerScreen(
          studioId = studioId,
          mediaId = mediaId,
          onBack = { navController.popBackStack() },
          onFullscreenChanged = { isFullscreen ->
            // Handle fullscreen changes if needed
          }
        )
      }
    }

    // Show upload screen as overlay on all authenticated screens
    if (startDestination != Route.Login.path) {
      Column(
        modifier = Modifier.fillMaxSize()
      ) {
        // Content area (fills available space)
        Box(modifier = Modifier.weight(1f))

        // Upload panel at bottom
        UploadScreen()
      }
    }
  }
}
