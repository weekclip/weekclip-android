package cc.sunglint.weekclip.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the session bearer token when there is one.
 *
 * An interceptor rather than a `@Header` parameter on every method: the token
 * is not the caller's business, and threading it through 58 endpoint signatures
 * is 58 chances to forget one (android-retrofit skill §3, "Global Headers").
 *
 * When [SessionTokenProvider] has no token the header is simply omitted. It is
 * not sent as `Bearer null` — that would arrive as a malformed credential and
 * come back 400 instead of 401, hiding "not logged in" behind "bad request".
 */
class AuthInterceptor(
  private val tokenProvider: SessionTokenProvider
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val token = tokenProvider.currentAccessToken()
    val request = if (token.isNullOrBlank()) {
      chain.request()
    } else {
      chain.request().newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
    }
    return chain.proceed(request)
  }
}
