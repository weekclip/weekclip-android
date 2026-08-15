package cc.sunglint.weekclip.core.session

/**
 * What the app currently knows about the signed-in profile.
 *
 * [Unknown] is a real state, not a placeholder: on a cold start the session
 * lives in encrypted storage and reading it is a suspend call. A UI that treats
 * "not loaded yet" as "signed out" flashes a login screen at a user who is
 * signed in — the same class of bug as 148.3d, where a refresh with no rows yet
 * rendered as "No studios yet."
 */
sealed interface SessionState {
  data object Unknown : SessionState

  data object SignedOut : SessionState

  data class SignedIn(val userId: String) : SessionState
}
