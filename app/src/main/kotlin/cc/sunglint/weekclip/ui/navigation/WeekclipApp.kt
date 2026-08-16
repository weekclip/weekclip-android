package cc.sunglint.weekclip.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import cc.sunglint.weekclip.ui.auth.AuthGateUiState
import cc.sunglint.weekclip.ui.auth.AuthGateViewModel
import cc.sunglint.weekclip.ui.auth.LoginRoute
import cc.sunglint.weekclip.ui.dashboard.DashboardRoute
import cc.sunglint.weekclip.ui.update.AppGateUiState
import cc.sunglint.weekclip.ui.update.AppGateViewModel
import cc.sunglint.weekclip.ui.update.UpdateRequiredScreen

/**
 * Application shell.
 *
 * Two gates wrap everything, in this order:
 *
 * 1. **version** — is this binary still allowed to talk to the server (D6①)
 * 2. **auth** — is there a profile, or is this a guest with a share link (D4)
 *
 * The order is not arbitrary. A version gate that needed a session could not
 * gate the login screen, and an app too old to run may also be too old to sign
 * in — 148.5a states this as the reason `GET /app/version` takes no credential.
 *
 * The route table below is the other real thing here: it is the contract deep
 * links resolve against.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WeekclipApp(
  /**
   * A route parsed from the intent that started or resumed the Activity, or
   * null. Hoisted into `MainActivity` rather than read here because the
   * Activity is what receives intents, and `onNewIntent` has to be handled at
   * the moment it fires — not on the next recomposition.
   */
  incomingRoute: String? = null,
  onIncomingRouteHandled: () -> Unit = {},
  gateViewModel: AppGateViewModel = hiltViewModel(),
  authViewModel: AuthGateViewModel = hiltViewModel()
) {
  val context = LocalContext.current
  val gate by gateViewModel.uiState.collectAsStateWithLifecycle()
  val auth by authViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(incomingRoute) {
    incomingRoute?.let {
      authViewModel.onIncomingLink(it)
      onIncomingRouteHandled()
    }
  }

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
    val contentModifier = Modifier.padding(innerPadding)

    // `Checking` renders nothing: starting at "allowed" would flash the app for
    // a frame before a block landed, and starting at "blocked" would flash a
    // force-update screen at everyone.
    when (val state = gate) {
      AppGateUiState.Checking -> Unit

      is AppGateUiState.UpdateRequired -> UpdateRequiredScreen(
        storeUrl = state.storeUrl,
        onOpenStore = { url ->
          context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        },
        modifier = contentModifier
      )

      AppGateUiState.Allowed -> when (val session = auth) {
        // Same reasoning as `Checking` above, one gate down.
        AuthGateUiState.Unknown -> Unit

        AuthGateUiState.SignedOut -> LoginRoute(
          onOpenAuthorizeUrl = { url -> openInSystemBrowser(context, url) },
          modifier = contentModifier
        )

        is AuthGateUiState.Guest -> GuestHost(
          route = session.route,
          onLeave = authViewModel::onLeaveGuest,
          modifier = contentModifier
        )

        AuthGateUiState.SignedIn -> SignedInHost(
          pendingNavigation = authViewModel,
          modifier = contentModifier
        )
      }
    }
  }
}

@Composable
private fun SignedInHost(
  pendingNavigation: AuthGateViewModel,
  modifier: Modifier = Modifier
) {
  val navController = rememberNavController()
  val pending by pendingNavigation.pendingNavigation.collectAsStateWithLifecycle()

  // Runs after the NavHost below exists, which is why the deep link is applied
  // here rather than as `startDestination`: the dashboard stays underneath, so
  // backing out of a link lands somewhere instead of closing the app.
  LaunchedEffect(pending) {
    pending?.let {
      navController.navigate(it)
      pendingNavigation.onNavigated()
    }
  }

  NavHost(
    navController = navController,
    startDestination = WeekclipRoutes.DASHBOARD,
    modifier = modifier
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

/**
 * The guest surface: one screen, no dashboard behind it.
 *
 * Deliberately not a `NavHost` with a different start destination. A guest has
 * nowhere else to go — every other route needs a profile — and a navigation
 * graph whose other entries are all unreachable is an invitation to wire one of
 * them up by accident.
 *
 * Backing out hands control to [onLeave], which puts the login screen up. The
 * alternative, letting back close the app, would leave someone who opened a
 * shared album no way to reach the account they may already have.
 */
@Composable
private fun GuestHost(
  route: String,
  onLeave: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onLeave)

  // Phase 5 replaces this with the real share viewer (PRD-0008). What is real
  // today is the gate around it: this composes with no session at all.
  PlaceholderScreen(name = "Share", modifier = modifier.testTag("guest-host"))
}

@Composable
private fun PlaceholderScreen(name: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
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

/**
 * Opens the OAuth authorize URL in the browser the user already trusts.
 *
 * **Not a `WebView`**, and this is not a style preference: Google refuses OAuth
 * from an embedded web view (`disallowed_useragent`), because an embedded view
 * can read the password as it is typed. RFC 8252 §8.12 says the same thing in
 * general terms. A Custom Tab is a real browser process wearing the app's
 * colours — which also means it already holds whatever Google session the user
 * has, so most people never see a password prompt at all.
 *
 * `CustomTabsIntent` degrades on its own: the intent it builds *is* an
 * `ACTION_VIEW`, and a browser with no Custom Tabs support ignores the extras
 * and opens the page normally. There is no fallback branch to keep correct.
 *
 * `FLAG_ACTIVITY_NEW_TASK` is deliberately absent. The tab opening inside this
 * task is what lets the redirect back into `MainActivity` (`singleTask`) clear
 * it away; in its own task it would linger behind the app, and backing out of
 * the app would land on a stale consent page.
 */
private fun openInSystemBrowser(context: Context, url: String) {
  CustomTabsIntent.Builder()
    // Nothing to show a title for — this is a sign-in, not a page visit.
    .setShowTitle(false)
    .build()
    .launchUrl(context, Uri.parse(url))
}
