package cc.sunglint.weekclip.core.network

import cc.sunglint.weekclip.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the bearer credential for a request, or null when there is none for
 * that [SessionAxis].
 *
 * This replaces the Phase-2 `SessionTokenProvider`, whose signature was
 * `currentAccessToken(): String?` — no request, so no way to answer "which
 * session is this request on". Its own KDoc flagged that gap and named this
 * task; the API contract read in 148.5 turned it from a foreseeable problem
 * into a demonstrable one (see [SessionAxis]).
 */
interface SessionCredentialProvider {

  /**
   * @param path the request's encoded path, e.g. `/api/v1/studios`.
   */
  fun credentialFor(path: String): String?

  /**
   * Called only after the server answered 401, with the credential that failed.
   * Returns a renewed one to retry with, or null to give up.
   */
  fun credentialAfterUnauthorized(path: String, failedCredential: String?): String?
}

/**
 * Bridges OkHttp's blocking interceptor world to [SessionManager]'s suspend API.
 *
 * `runBlocking` is correct here rather than merely convenient: interceptors and
 * authenticators are invoked on OkHttp's own dispatcher threads, never on the
 * main thread, and the call they are about to make is going to block that
 * thread anyway. Threading a suspend context into `Interceptor.intercept` is
 * not possible without giving up the interceptor (and with it the guarantee
 * that no endpoint can forget its auth header).
 *
 * The guest axis returns null: nothing stores a share session yet, because no
 * screen mints one. The seam takes the path so that when the share viewer
 * arrives (PRD-0008 D5, task 148.7) it plugs in here without the interceptor,
 * the authenticator or a single repository changing.
 */
@Singleton
class SessionManagerCredentialProvider @Inject constructor(
  private val sessionManager: SessionManager
) : SessionCredentialProvider {

  override fun credentialFor(path: String): String? = when (SessionAxis.of(path)) {
    SessionAxis.Profile -> runBlocking { sessionManager.accessToken() }
    SessionAxis.Guest -> null
  }

  override fun credentialAfterUnauthorized(path: String, failedCredential: String?): String? =
    when (SessionAxis.of(path)) {
      SessionAxis.Profile -> runBlocking {
        sessionManager.accessTokenAfterUnauthorized(failedCredential)
      }
      // A share session cannot be renewed: it is minted by entering the link's
      // password and expires on its own schedule (`SHARE_LINK_SESSION_TTL_MS`).
      // Retrying is the guest re-entering the password, not a token exchange.
      SessionAxis.Guest -> null
    }
}
