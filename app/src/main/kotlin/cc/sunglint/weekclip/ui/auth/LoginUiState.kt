package cc.sunglint.weekclip.ui.auth

import cc.sunglint.weekclip.core.result.AppError

/**
 * The login screen in one immutable value.
 *
 * The four states below are the four the wireframe draws
 * (`weekclip-design-system/apps/wireframes/screens/auth/mobile.html`): the
 * form, `loading`, `error`, and `denied`. They are one `phase` plus one `error`
 * rather than four booleans for the reason `DashboardUiState` gives — separate
 * flags let the UI render a combination that never legally exists.
 */
data class LoginUiState(
  val phase: LoginPhase = LoginPhase.Idle,
  val error: LoginError? = null,
  /**
   * A deep link is waiting behind the sign-in. The screen says so, because the
   * wireframe's "딥링크 복귀" zone exists to answer the question a user asks
   * when an unexpected login screen appears: *did it forget what I tapped?*
   */
  val hasIntendedDestination: Boolean = false,
  /**
   * The label of a debug-only way in, or null when there is none.
   *
   * Always null in a release APK — the binding lives in the debug source set,
   * so the button is absent rather than hidden. The label travels with the
   * binding for the same reason (see `DebugSignInAction.label`).
   */
  val debugSignInLabel: String? = null
) {
  /**
   * The whole point of the `loading` state in the wireframe: *"앱은 외부
   * 브라우저로 나갔다 돌아온다. 돌아오는 동안 이 화면이 남아 있어야 한다."*
   * Both phases keep the screen — one waiting for the browser, one waiting for
   * the exchange — and the user cannot tell them apart, which is correct.
   */
  val isBusy: Boolean get() = phase != LoginPhase.Idle
}

enum class LoginPhase {
  Idle,

  /** Handed off to the system browser; waiting for a redirect or a cancel. */
  Connecting,

  /** Redirect received; trading the authorization code for a session. */
  Exchanging
}

/**
 * Why sign-in did not happen, in the terms the screen distinguishes.
 *
 * Not `AppError` directly: the two states the wireframe draws separately —
 * "로그인하지 못했다" (retry works) and "돌아갈 곳을 잃었다" (retry does not) —
 * are not a distinction `AppError` makes, and never should be. It is about
 * transport; this is about the sign-in.
 */
sealed interface LoginError {

  /** Google or the user refused. Retrying just shows the same prompt. */
  data class Denied(val reason: String) : LoginError

  /**
   * No Supabase project key in this build, so there is nothing to sign in to.
   * A state, not a bug — see `AuthConfig.isConfigured`.
   */
  data object NotConfigured : LoginError

  /** The round trip broke somewhere. Retrying is the sensible move. */
  data class Failed(val cause: AppError) : LoginError
}
