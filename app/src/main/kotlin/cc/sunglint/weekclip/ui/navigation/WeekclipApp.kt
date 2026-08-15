package cc.sunglint.weekclip.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cc.sunglint.weekclip.ui.dashboard.DashboardRoute

/**
 * Application shell.
 *
 * Dashboard is a real screen — it is the vertical slice that proves the spine
 * (Hilt -> ViewModel -> use case -> repository -> Retrofit -> the live
 * `GET /studios` contract) is connected end to end. The rest are still
 * placeholders and land in Phase 5 (PRD-0008).
 *
 * The route table is the other real thing here: it is the contract deep links
 * resolve against.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WeekclipApp() {
  val navController = rememberNavController()

  // Compose publishes accessibility nodes with `text` but an empty
  // `resource-id`, so UI-automation selectors written as `id: ...` match
  // nothing — measured against a real device on 2026-08-14. This opt-in makes
  // `Modifier.testTag("x")` surface as that node's resource-id, which is what
  // `maestro/` flows select on. It has to sit at the root: the property applies
  // to the whole subtree, and retrofitting it later means touching every
  // screen. Costs nothing at runtime beyond one semantics property.
  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .semantics { testTagsAsResourceId = true }
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = WeekclipRoutes.DASHBOARD,
      modifier = Modifier.padding(innerPadding)
    ) {
      composable(WeekclipRoutes.DASHBOARD) {
        DashboardRoute(
          onStudioClick = { studioId -> navController.navigate(WeekclipRoutes.studio(studioId)) }
        )
      }
      composable(WeekclipRoutes.STUDIO) { PlaceholderScreen("Studio") }
      composable(WeekclipRoutes.MEDIA) { PlaceholderScreen("Media") }
      composable(WeekclipRoutes.MEMBERS) { PlaceholderScreen("Members") }
      composable(WeekclipRoutes.CAPACITY) { PlaceholderScreen("Capacity") }
      composable(WeekclipRoutes.INVITE) { PlaceholderScreen("Invite") }
      composable(WeekclipRoutes.SHARE) { PlaceholderScreen("Share") }
    }
  }
}

@Composable
private fun PlaceholderScreen(name: String) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Tagged so `maestro/` can select the screen without depending on its copy.
    Text(
      text = name,
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.testTag("screen-title")
    )
    Text(text = "Not implemented yet", style = MaterialTheme.typography.bodyMedium)
  }
}
