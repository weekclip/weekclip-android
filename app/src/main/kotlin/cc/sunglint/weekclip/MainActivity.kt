package cc.sunglint.weekclip

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.sunglint.weekclip.core.auth.AuthRedirectBus
import cc.sunglint.weekclip.core.auth.OAuthCallback
import cc.sunglint.weekclip.core.auth.SupabaseOAuth
import cc.sunglint.weekclip.ui.navigation.WeekclipApp
import cc.sunglint.weekclip.ui.navigation.WeekclipDeepLink
import cc.sunglint.weekclip.ui.theme.WeekclipTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single Activity, and therefore the single place URLs enter this app.
 *
 * Two unrelated kinds of URL arrive at the same door, and telling them apart is
 * the whole of [handleIntent]:
 *
 * | URL | what it is | where it goes |
 * |-----|-----------|---------------|
 * | `cc.sunglint.weekclip://auth-callback?...` | our own OAuth redirect | [AuthRedirectBus] |
 * | `https://weekclip.com/...` | a link someone shared | `WeekclipDeepLink` -> the shell |
 *
 * They take different paths for a reason. A redirect has to be readable at a
 * precise moment in the lifecycle — see [AuthRedirectBus] — while a deep link
 * only has to reach the navigation graph eventually.
 *
 * ⚠️ **`launchMode="singleTask"` is load-bearing.** The Custom Tab opens inside
 * this task; without `singleTask` the redirect would create a *second*
 * `MainActivity` on top of it, with a fresh ViewModel that never sent the
 * request it is now receiving the answer to. `singleTask` instead delivers the
 * intent to the existing instance via [onNewIntent] and clears the tab away.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject
  lateinit var redirectBus: AuthRedirectBus

  /**
   * The route from the most recent intent, or null.
   *
   * Compose state rather than a parameter read once, because `onNewIntent`
   * fires long after `setContent`: a link tapped while the app is already open
   * has to reach the shell too.
   */
  private var incomingRoute by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Before setContent, so a cold start from a link composes already knowing
    // about it instead of rendering the dashboard and then jumping.
    handleIntent(intent)

    setContent {
      WeekclipTheme {
        WeekclipApp(
          incomingRoute = incomingRoute,
          onIncomingRouteHandled = { incomingRoute = null }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // Required by the platform contract: `getIntent()` keeps returning the
    // original launch intent otherwise, and anything that re-reads it later
    // (a configuration change, a restore) would replay a stale link.
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent) {
    val data = intent.data ?: return

    when (val callback = SupabaseOAuth.parseCallback(data)) {
      // Not our redirect. It may still be a link this app owns; a URL that is
      // neither is dropped rather than guessed at, because PRD-0008 D4 keeps
      // landing, policy and pre-login pages on the web on purpose.
      OAuthCallback.NotACallback -> WeekclipDeepLink.routeOf(data)?.let { incomingRoute = it }

      else -> redirectBus.offer(callback)
    }
  }
}
