package com.weekclip.android.platform

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.weekclip.android.auth.AuthViewModel
import com.weekclip.android.auth.ui.LoginScreen
import com.weekclip.android.studio.ui.MediaListScreen
import com.weekclip.android.studio.ui.StudioListScreen
import androidx.hilt.navigation.compose.hiltViewModel

sealed class Route(val path: String) {
  data object Login : Route("login")
  data object StudioList : Route("studios")
  data class MediaList(val studioId: String) : Route("studios/$studioId/media")

  companion object {
    fun mediaList(studioId: String) = "studios/$studioId/media"
  }
}

@Composable
fun NavigationHost(
  authViewModel: AuthViewModel = hiltViewModel(),
  startDestination: String = Route.StudioList.path
) {
  val navController = rememberNavController()

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
          // Phase 2: Navigate to video player
          // navController.navigate(Route.videoPlayer(studioId, mediaId))
        }
      )
    }
  }
}
