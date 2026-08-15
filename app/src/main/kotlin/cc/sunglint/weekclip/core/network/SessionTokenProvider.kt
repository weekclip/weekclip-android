package cc.sunglint.weekclip.core.network

/**
 * Supplies the bearer token for API calls, or null when there is no session.
 *
 * This is a seam, not an implementation. PRD-0008 D6 makes token storage one of
 * the three app-support cores (task 148.5), and D5 adds a second axis on top of
 * it — a non-logged-in guest holds a *share session*, not a profile session, so
 * whatever lands here has to answer "which session is this request on".
 *
 * Until then [NoSessionTokenProvider] is bound. Requests go out unauthenticated
 * and come back 401, which surfaces as [cc.sunglint.weekclip.core.result.AppError.Unauthorized]
 * — the honest result for an app with no login yet, and one the UI already
 * renders.
 */
fun interface SessionTokenProvider {
  fun currentAccessToken(): String?
}

/** The Phase-2 binding: there is no session, and the code says so. */
class NoSessionTokenProvider : SessionTokenProvider {
  override fun currentAccessToken(): String? = null
}
