package cc.sunglint.weekclip.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.sunglint.weekclip.R
import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.ui.theme.WeekclipTheme
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.LaunchedEffect

/**
 * Stateful entry point for the first gate.
 *
 * Layout follows the wireframe (`screens/auth/mobile.html`): identity, then one
 * way in, then — app-only — what you will be taken back to. The notes are
 * explicit that there is exactly one button, and the reason is worth keeping in
 * front of whoever adds the second one: *"방법이 둘이면 재방문 때 '지난번에 뭘로
 * 들어왔지'가 생기고, 그 순간 이탈이 난다."*
 */
@Composable
fun LoginRoute(
  onOpenAuthorizeUrl: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LoginViewModel = hiltViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Every resume, not just returns from the browser. The ViewModel needs the
  // first one too — a redirect can arrive while this screen is still being
  // composed, and it is waiting in the bus by then.
  LifecycleResumeEffect(viewModel) {
    viewModel.onReturnedToForeground()
    onPauseOrDispose {}
  }

  LaunchEffects(viewModel.effects, onOpenAuthorizeUrl)

  LoginScreen(
    uiState = uiState,
    onGoogleSignIn = viewModel::onGoogleSignInClick,
    onRetry = viewModel::onRetry,
    onDebugSignIn = viewModel::onDebugSignInClick,
    modifier = modifier
  )
}

@Composable
private fun LaunchEffects(effects: Flow<LoginEffect>, onOpenAuthorizeUrl: (String) -> Unit) {
  LaunchedEffect(effects) {
    effects.collect { effect ->
      when (effect) {
        is LoginEffect.OpenAuthorizeUrl -> onOpenAuthorizeUrl(effect.url)
      }
    }
  }
}

/** Stateless. Previewable, and testable without Hilt or a coroutine scheduler. */
@Composable
fun LoginScreen(
  uiState: LoginUiState,
  onGoogleSignIn: () -> Unit,
  onRetry: () -> Unit,
  onDebugSignIn: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .testTag("login-screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.login_title),
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .semantics { heading() }
        .testTag("screen-title")
    )
    Text(
      text = stringResource(R.string.login_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )

    if (uiState.isBusy) {
      // The wireframe's `loading` state, and its note is the requirement:
      // "앱은 외부 브라우저로 나갔다 돌아온다. 돌아오는 동안 이 화면이 남아 있어야
      // 한다." Replacing the button rather than covering the screen is what keeps
      // it the same screen.
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MIN_TOUCH_TARGET)
          .testTag("login-connecting"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        CircularProgressIndicator()
        Text(
          text = stringResource(R.string.login_connecting),
          style = MaterialTheme.typography.bodySmall
        )
      }
    } else {
      Button(
        onClick = onGoogleSignIn,
        modifier = Modifier
          .fillMaxWidth()
          // 48dp is the accessibility floor for anything tappable
          // (android-accessibility skill §2). Stating it means a padding change
          // cannot quietly drop below it.
          .heightIn(min = MIN_TOUCH_TARGET)
          .testTag("login-google")
      ) {
        Text(text = stringResource(R.string.login_google))
      }
    }

    if (uiState.hasIntendedDestination) {
      // App-only zone in the wireframe ("딥링크 복귀"). It is not decoration:
      // an unexpected login screen reads as "it forgot what I tapped" unless
      // something says otherwise.
      Text(
        text = stringResource(R.string.login_return_to_link),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("login-intended-destination")
      )
    }

    uiState.error?.let { error ->
      Column(
        modifier = Modifier
          .fillMaxWidth()
          // Announced when it appears — a user relying on TalkBack has no other
          // signal that the button they pressed produced an error further down.
          .semantics { liveRegion = LiveRegionMode.Polite }
          .testTag("login-error"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(error.messageRes()),
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center
        )
        // No retry for NotConfigured: pressing it again cannot compile a project
        // key into the running binary. The wireframe draws the same split —
        // `error` gets a button, `denied` does not.
        if (error !is LoginError.NotConfigured) {
          OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("login-retry")) {
            Text(text = stringResource(R.string.action_retry))
          }
        }
      }
    }

    uiState.debugSignInLabel?.let { label ->
      TextButton(
        onClick = onDebugSignIn,
        modifier = Modifier
          .heightIn(min = MIN_TOUCH_TARGET)
          .testTag("login-debug")
      ) {
        // Not a string resource: this button does not exist in a release build,
        // and a resource that only debug code reads would ship anyway.
        Text(text = label)
      }
    }
  }
}

private val MIN_TOUCH_TARGET = 48.dp

/**
 * Error -> copy.
 *
 * A `when` over the sealed set, so a new [LoginError] fails to compile here
 * rather than falling through to a message nobody notices is wrong — the same
 * rule `DashboardScreen` states for `AppError`.
 */
private fun LoginError.messageRes(): Int = when (this) {
  is LoginError.Denied -> R.string.login_error_denied
  LoginError.NotConfigured -> R.string.login_error_not_configured
  is LoginError.Failed -> when (cause) {
    AppError.Offline -> R.string.error_offline
    AppError.Timeout -> R.string.error_timeout
    else -> R.string.login_error_failed
  }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
  WeekclipTheme {
    LoginScreen(
      uiState = LoginUiState(),
      onGoogleSignIn = {},
      onRetry = {},
      onDebugSignIn = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenConnectingPreview() {
  WeekclipTheme {
    LoginScreen(
      uiState = LoginUiState(phase = LoginPhase.Connecting, hasIntendedDestination = true),
      onGoogleSignIn = {},
      onRetry = {},
      onDebugSignIn = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
  WeekclipTheme {
    LoginScreen(
      uiState = LoginUiState(error = LoginError.Failed(AppError.Offline)),
      onGoogleSignIn = {},
      onRetry = {},
      onDebugSignIn = {}
    )
  }
}
