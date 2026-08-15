package cc.sunglint.weekclip.core.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Renews the session when the server says 401, and replays the request once.
 *
 * [AuthInterceptor] refreshes *before* expiry, which handles the ordinary
 * hourly rotation. This handles everything that clock arithmetic cannot know
 * about: a token revoked server-side, a device whose clock is wrong, a session
 * invalidated because the account was suspended. Those all look identical from
 * here — a 401 on a request that carried a credential — and the response is the
 * same: renew, retry once, and if that fails let the 401 through so the UI can
 * show `AppError.Unauthorized`.
 *
 * OkHttp calls an `Authenticator` only for 401s, after the response is in hand,
 * and re-runs the whole interceptor chain on the returned request. That is why
 * this is not an interceptor: an interceptor cannot see the 401 and re-issue
 * without hand-rolling the retry, and hand-rolled retries are how request loops
 * get shipped.
 */
class SessionAuthenticator(
  private val credentials: SessionCredentialProvider
) : Authenticator {

  override fun authenticate(route: Route?, response: Response): Request? {
    // Guard against a loop: if the server 401s the retry too, stop. Without
    // this, a provider that keeps handing back a credential the server keeps
    // rejecting would ping-pong until the call times out.
    if (response.priorResponse != null) {
      return null
    }

    val failed = response.request
      .header(AuthInterceptor.AUTHORIZATION)
      ?.removePrefix(AuthInterceptor.BEARER_PREFIX)

    val renewed = credentials.credentialAfterUnauthorized(
      response.request.url.encodedPath,
      failed
    ) ?: return null

    // Retrying with the identical credential is a guaranteed second 401. This
    // is reachable — `accessTokenAfterUnauthorized` returns the stored token
    // unchanged when another request refreshed first, and that other refresh
    // may have produced the very token that just failed.
    if (renewed == failed) {
      return null
    }

    return response.request.newBuilder()
      .header(AuthInterceptor.AUTHORIZATION, "${AuthInterceptor.BEARER_PREFIX}$renewed")
      .build()
  }
}
