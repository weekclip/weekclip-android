package cc.sunglint.weekclip.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.sunglint.weekclip.core.auth.AuthRedirectBus
import cc.sunglint.weekclip.core.auth.OAuthCallback
import cc.sunglint.weekclip.core.auth.SignInFlowStore
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.domain.usecase.BeginGoogleSignInUseCase
import cc.sunglint.weekclip.domain.usecase.CompleteGoogleSignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The first gate's state holder.
 *
 * ### The one-frame problem this ViewModel does not have
 *
 * It never decides whether the user is signed in. `AuthGateViewModel` does that
 * from `SessionManager`, and this screen only exists once that answer is
 * `SignedOut`. Asking here as well would give two owners of one fact and, worse,
 * a moment where they disagree.
 *
 * ### Cancellation is inferred, not reported
 *
 * Android tells an app nothing when the user backs out of a Custom Tab. What it
 * does guarantee is the ordering `AuthRedirectBus` documents: a redirect is
 * delivered to `onNewIntent` **before** `onResume`. So [onReturnedToForeground]
 * — called from the screen's resume — draining the bus and finding it empty is
 * not a guess. It means no redirect arrived, which for a screen in
 * [LoginPhase.Connecting] means the user came back without finishing.
 *
 * Without this the spinner would stay up forever on a cancelled sign-in, and
 * the only way out would be to kill the app.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
  private val beginGoogleSignIn: BeginGoogleSignInUseCase,
  private val completeGoogleSignIn: CompleteGoogleSignInUseCase,
  private val redirectBus: AuthRedirectBus,
  private val flowStore: SignInFlowStore,
  private val debugSignIns: Set<@JvmSuppressWildcards DebugSignInAction>
) : ViewModel() {

  val uiState: StateFlow<LoginUiState>
    field = MutableStateFlow(LoginUiState(debugSignInLabel = debugSignIns.firstOrNull()?.label))

  /**
   * Opening a browser is an action, not a state — replaying it on the next
   * recomposition would launch a second Custom Tab.
   *
   * A `Channel` rather than the `SharedFlow` the ViewModel skill reaches for
   * first: a `SharedFlow` with no active collector **drops**, and the window in
   * which this screen has no collector is exactly the window in which it is
   * being composed for the first time — which is when the user's tap arrives.
   * `Channel` buffers until someone reads. This is the event stream
   * `DashboardViewModel`'s note says it deliberately did not build until there
   * was an event to send; this is that event.
   */
  private val effectChannel = Channel<LoginEffect>(Channel.BUFFERED)

  val effects: Flow<LoginEffect> = effectChannel.receiveAsFlow()

  init {
    viewModelScope.launch {
      uiState.update { it.copy(hasIntendedDestination = flowStore.hasIntendedRoute()) }
    }
  }

  fun onGoogleSignInClick() {
    if (uiState.value.isBusy) return

    viewModelScope.launch {
      uiState.update { it.copy(phase = LoginPhase.Connecting, error = null) }

      val url = beginGoogleSignIn()
      if (url == null) {
        uiState.update { it.copy(phase = LoginPhase.Idle, error = LoginError.NotConfigured) }
        return@launch
      }
      effectChannel.send(LoginEffect.OpenAuthorizeUrl(url))
    }
  }

  /**
   * Called every time the screen resumes, including the very first time. See
   * the class note: an empty bus while [LoginPhase.Connecting] is a cancelled
   * sign-in, and an empty bus while [LoginPhase.Idle] is just the screen
   * appearing.
   */
  fun onReturnedToForeground() {
    when (val callback = redirectBus.take()) {
      is OAuthCallback.Granted -> exchange(callback.code)

      is OAuthCallback.Denied -> {
        // The round trip is over and the verifier will never be paired. Leaving
        // it would mean the *next* sign-in's redirect could be matched against
        // an abandoned one.
        viewModelScope.launch { flowStore.takeVerifier() }
        uiState.update {
          it.copy(phase = LoginPhase.Idle, error = LoginError.Denied(callback.error))
        }
      }

      // `null` and NotACallback are the same thing here: nothing came back.
      else -> if (uiState.value.phase == LoginPhase.Connecting) {
        viewModelScope.launch { flowStore.takeVerifier() }
        uiState.update { it.copy(phase = LoginPhase.Idle) }
      }
    }
  }

  /** Clears the error and lets the button be pressed again. */
  fun onRetry() {
    uiState.update { it.copy(error = null) }
    onGoogleSignInClick()
  }

  /**
   * Debug builds only. Signing in with a password puts a real token in front of
   * the session code without a Google round trip, which is what makes the store
   * and the gate testable on a device before the redirect allow list exists.
   * The set is empty in a release build, so this is unreachable there — see
   * [DebugSignInAction].
   */
  fun onDebugSignInClick() {
    val action = debugSignIns.firstOrNull() ?: return
    if (uiState.value.isBusy) return

    viewModelScope.launch {
      uiState.update { it.copy(phase = LoginPhase.Exchanging, error = null) }
      val signedIn = action.signIn()
      // On success the gate flips to SignedIn and this screen leaves; setting
      // Idle first would put the form back for a frame underneath it.
      if (!signedIn) {
        uiState.update { it.copy(phase = LoginPhase.Idle, error = LoginError.NotConfigured) }
      }
    }
  }

  private fun exchange(code: String) {
    viewModelScope.launch {
      uiState.update { it.copy(phase = LoginPhase.Exchanging, error = null) }

      when (val result = completeGoogleSignIn(code)) {
        // Deliberately no state change: adopting the session flips
        // `SessionManager` to SignedIn, the gate swaps this screen out, and
        // anything set here would only be visible as a flash on the way off.
        is AppResult.Success -> Unit

        is AppResult.Failure -> uiState.update {
          it.copy(phase = LoginPhase.Idle, error = LoginError.Failed(result.error))
        }
      }
    }
  }
}

/** One-shot instructions to the screen. */
sealed interface LoginEffect {
  data class OpenAuthorizeUrl(val url: String) : LoginEffect
}
