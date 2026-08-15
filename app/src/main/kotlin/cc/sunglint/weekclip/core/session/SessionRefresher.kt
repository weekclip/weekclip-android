package cc.sunglint.weekclip.core.session

/**
 * Exchanges a refresh token for a fresh grant.
 *
 * A port so [SessionManager] can be tested without a socket, and so the
 * identity provider stays one implementation away.
 */
interface SessionRefresher {
  suspend fun refresh(session: ProfileSession): RefreshOutcome
}

/**
 * Three outcomes, not two.
 *
 * The distinction between [Rejected] and [Unavailable] is the whole point. A
 * refresh that fails because the phone is in a lift must **not** sign the user
 * out — they would come out of the lift to a login screen with nothing to log
 * in with. Only the provider explicitly refusing the refresh token means the
 * session is actually gone.
 *
 * weekclip-web reached the same conclusion independently and encodes it in
 * `backendSession.ts`: `api-failure` (network, 5xx) leaves the session alone,
 * `backend-rejected` (401/403) signs out. Two clients disagreeing about when a
 * user is logged out is a support ticket nobody can reproduce.
 */
sealed interface RefreshOutcome {
  data class Refreshed(val session: ProfileSession) : RefreshOutcome

  /** The provider refused the refresh token. It will never work again. */
  data object Rejected : RefreshOutcome

  /** Transient — no network, a timeout, a 5xx. Keep the session, try later. */
  data object Unavailable : RefreshOutcome
}
