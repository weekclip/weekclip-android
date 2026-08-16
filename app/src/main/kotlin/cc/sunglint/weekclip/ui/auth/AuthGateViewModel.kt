package cc.sunglint.weekclip.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.sunglint.weekclip.core.auth.SignInFlowStore
import cc.sunglint.weekclip.core.session.SessionManager
import cc.sunglint.weekclip.core.session.SessionState
import cc.sunglint.weekclip.ui.navigation.WeekclipRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Who is allowed past the front door, and where they land.
 *
 * PRD-0008 D4 put every product surface behind a profile except one. This is
 * that rule as code:
 *
 * | entry | gate |
 * |-------|------|
 * | app icon, or any link under `/dashboard` · `/studios` · `/invite` | sign in first |
 * | a share link (`/share/:token`) | straight through, no account |
 *
 * The exception is not a convenience. `SessionAxis` already says a share link
 * carries **its own** credential — an HMAC over the link's key, not a profile
 * JWT — so a guest is not a signed-out user waiting to sign in; they are a
 * different kind of caller the API already knows how to answer. Requiring a
 * profile there would make the app refuse a link that a browser opens fine.
 *
 * Invites are deliberately on the other side of that line. Accepting one
 * attaches a studio to an account, so there has to be an account first; the web
 * flow reaches the same conclusion (F3, "초대 수락 전 로그인").
 *
 * ### [AuthGateUiState.Unknown] renders nothing, on purpose
 *
 * Reading the stored session is a suspend call — it decrypts. Treating "not
 * loaded yet" as "signed out" flashes a login screen at a user who is signed
 * in, which is the failure `SessionState` warns about and the same shape as the
 * version gate's `Checking`.
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
  private val sessionManager: SessionManager,
  private val flowStore: SignInFlowStore
) : ViewModel() {

  val uiState: StateFlow<AuthGateUiState>
    field = MutableStateFlow<AuthGateUiState>(AuthGateUiState.Unknown)

  /**
   * A route the shell should open once it is showing a `NavHost`.
   *
   * Separate from [uiState] because it is an instruction, not a condition: the
   * shell consumes it with [onNavigated] and it must not fire twice.
   */
  val pendingNavigation: StateFlow<String?>
    field = MutableStateFlow<String?>(null)

  /**
   * Held rather than acted on until the session is known. A link can arrive
   * before the store has been read, and deciding then would send a signed-in
   * user to the login screen.
   */
  private var unresolvedLink: String? = null

  /** Sticky for the life of the guest visit — see [onLeaveGuest]. */
  private var guestRoute: String? = null

  init {
    viewModelScope.launch {
      sessionManager.state.collect { resolve(it) }
    }
    // Kicks the store read. `state` starts at Unknown and only leaves it here.
    viewModelScope.launch { sessionManager.reload() }
  }

  /** @param route already resolved by `WeekclipDeepLink`; never a raw URL. */
  fun onIncomingLink(route: String) {
    unresolvedLink = route
    viewModelScope.launch { resolve(sessionManager.state.value) }
  }

  fun onNavigated() {
    pendingNavigation.value = null
  }

  /**
   * The guest backed out of the shared album.
   *
   * They land on the login screen rather than a dashboard, because a guest does
   * not have one. This is the only way out of [AuthGateUiState.Guest] — a
   * signed-out user has nothing else in the app to reach.
   */
  fun onLeaveGuest() {
    guestRoute = null
    viewModelScope.launch { resolve(sessionManager.state.value) }
  }

  private suspend fun resolve(session: SessionState) {
    when (session) {
      SessionState.Unknown -> uiState.value = AuthGateUiState.Unknown

      is SessionState.SignedIn -> {
        // A profile outranks a guest visit: the same link opens inside the app
        // proper, where `SessionAxis` picks the right credential per request.
        guestRoute = null

        val link = unresolvedLink?.also { unresolvedLink = null }
        // The link that just arrived wins over one parked earlier — it is what
        // the user tapped most recently. The parked one is still consumed so it
        // cannot resurface on a later sign-in.
        val parked = flowStore.takeIntendedRoute()
        (link ?: parked)?.let { pendingNavigation.value = it }

        uiState.value = AuthGateUiState.SignedIn
      }

      SessionState.SignedOut -> {
        unresolvedLink?.let { link ->
          unresolvedLink = null
          if (WeekclipRoutes.isGuestRoute(link)) {
            guestRoute = link
          } else {
            // Parked on disk, not in this ViewModel: signing in leaves the
            // process (`SignInFlowStore`).
            flowStore.putIntendedRoute(link)
          }
        }

        uiState.value = guestRoute
          ?.let { AuthGateUiState.Guest(it) }
          ?: AuthGateUiState.SignedOut
      }
    }
  }
}

sealed interface AuthGateUiState {

  /** The stored session has not been read yet. Renders nothing. */
  data object Unknown : AuthGateUiState

  /** Show the first gate. */
  data object SignedOut : AuthGateUiState

  /** A share link opened without an account. [route] is the only reachable screen. */
  data class Guest(val route: String) : AuthGateUiState

  /** Show the app. */
  data object SignedIn : AuthGateUiState
}
