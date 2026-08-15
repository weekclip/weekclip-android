package cc.sunglint.weekclip.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the session bearer credential when there is one for this request.
 *
 * An interceptor rather than a `@Header` parameter on every method: the token
 * is not the caller's business, and threading it through 58 endpoint signatures
 * is 58 chances to forget one (android-retrofit skill §3, "Global Headers").
 *
 * The credential is chosen **per request path**, not globally — see
 * [SessionAxis] for why weekclip has two of them and what goes wrong when the
 * profile token is put on a guest route.
 *
 * When there is no credential the header is simply omitted. It is not sent as
 * `Bearer null` — that would arrive as a malformed credential and come back 400
 * instead of 401, hiding "not logged in" behind "bad request".
 */
class AuthInterceptor(
  private val credentials: SessionCredentialProvider
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val credential = credentials.credentialFor(request.url.encodedPath)

    return chain.proceed(
      if (credential.isNullOrBlank()) {
        request
      } else {
        request.newBuilder().header(AUTHORIZATION, "$BEARER_PREFIX$credential").build()
      }
    )
  }

  companion object {
    const val AUTHORIZATION = "Authorization"
    const val BEARER_PREFIX = "Bearer "
  }
}
