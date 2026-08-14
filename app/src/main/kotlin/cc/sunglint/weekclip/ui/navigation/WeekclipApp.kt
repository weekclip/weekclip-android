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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Skeleton shell. Every destination below is a placeholder — feature screens
 * land in Phase 5 (PRD-0008). What is real here is the route table: it is the
 * contract deep links resolve against.
 */
@Composable
fun WeekclipApp() {
  val navController = rememberNavController()

  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = WeekclipRoutes.DASHBOARD,
      modifier = Modifier.padding(innerPadding)
    ) {
      composable(WeekclipRoutes.DASHBOARD) { PlaceholderScreen("Dashboard") }
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
    Text(text = name, style = MaterialTheme.typography.headlineMedium)
    Text(text = "Not implemented yet", style = MaterialTheme.typography.bodyMedium)
  }
}
