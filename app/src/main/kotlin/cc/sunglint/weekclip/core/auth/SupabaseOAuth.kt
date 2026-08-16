package cc.sunglint.weekclip.core.auth

import android.net.Uri
import cc.sunglint.weekclip.core.session.AuthConfig
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The two pure ends of the Google sign-in round trip: the URL the browser is
 * sent to, and what comes back.
 *
 * ### No Google OAuth client of our own is involved
 *
 * This was the assumption that made 148.5c-b look blocked. The app does **not**
 * talk to Google. It opens *Supabase's* `/auth/v1/authorize`, Supabase redirects
 * to Google with **Supabase's own web client id**, and Google redirects back to
 * **Supabase's** HTTPS callback. Only then does Supabase redirect to
 * [REDIRECT_URI]. Google never sees a custom scheme, so there is no Android or
 * iOS OAuth client to register — measured 2026-08-16: the dev project's
 * `/authorize?provider=google` answers 302 to `accounts.google.com` with
 * `redirect_uri=https://<project>.supabase.co/auth/v1/callback`.
 *
 * What *is* required is one console line per project: [REDIRECT_URI] has to be
 * in Supabase Auth's **Redirect URLs** allow list. GoTrue validates
 * `redirect_to` at callback time, not at `/authorize` time — a missing entry
 * therefore looks like a browser that completes the Google prompt and then sits
 * on the website instead of returning, with no error anywhere. That failure
 * mode is why [OAuthCallback.NotACallback] exists rather than being folded into
 * a generic failure.
 *
 * ### Everything here is pure
 *
 * No `Context`, no network, no storage. That is what lets the wire format be
 * pinned by unit tests instead of by a device.
 */
object SupabaseOAuth {

  /**
   * Where Supabase sends the browser once it has the grant.
   *
   * The scheme is the application id, which is the convention that keeps two
   * apps from colliding by accident (RFC 8252 §7.1 "private-use URI scheme").
   * It is a convention, not a guarantee — see [PkceChallenge] on why PKCE is
   * what actually makes the redirect safe.
   *
   * Registered in `AndroidManifest.xml` on `MainActivity`, and it must stay
   * byte-identical to the Supabase allow-list entry: GoTrue compares the string,
   * so a trailing slash is a different URL.
   */
  const val REDIRECT_URI = "cc.sunglint.weekclip://auth-callback"

  /** The one provider weekclip offers (weekclip-web `LoginPage.tsx`). */
  const val PROVIDER_GOOGLE = "google"

  /**
   * The URL to hand to the system browser.
   *
   * @return null when this build has no Supabase project compiled in. Release
   *   builds used to ship that way on purpose (see [AuthConfig]); returning
   *   null keeps the caller from opening a browser at `https://auth.invalid`.
   */
  fun authorizeUrl(
    config: AuthConfig,
    challenge: PkceChallenge,
    provider: String = PROVIDER_GOOGLE,
    redirectUri: String = REDIRECT_URI
  ): String? {
    if (!config.isConfigured) return null

    val query = listOf(
      "provider" to provider,
      "redirect_to" to redirectUri,
      "code_challenge" to challenge.challenge,
      "code_challenge_method" to PkceChallenge.METHOD
    ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }

    // tokenBaseUrl carries the trailing slash Retrofit needs; `authorize` is a
    // sibling of `token` under the same /auth/v1.
    return "${config.tokenBaseUrl}authorize?$query"
  }

  /**
   * Classifies a URL the OS handed us.
   *
   * @param uri whatever arrived in the intent. Anything that is not our
   *   redirect — an App Link, a launcher intent with no data — comes back as
   *   [OAuthCallback.NotACallback] rather than throwing, because this is called
   *   on *every* incoming intent.
   */
  fun parseCallback(uri: Uri): OAuthCallback = parseCallback(uri.toString())

  fun parseCallback(raw: String): OAuthCallback {
    if (!raw.startsWith("$REDIRECT_URI?") && raw != REDIRECT_URI) {
      return OAuthCallback.NotACallback
    }

    val params = queryOf(raw)

    params["code"]?.takeIf { it.isNotBlank() }?.let { return OAuthCallback.Granted(it) }

    // GoTrue names the machine-readable field `error_code` and the OAuth spec
    // names it `error`; which one arrives depends on where in the chain the
    // refusal happened. Reading whichever is present beats guessing.
    val error = params["error_code"] ?: params["error"]
    if (error != null) {
      return OAuthCallback.Denied(
        error = error,
        description = params["error_description"]
      )
    }

    // Our scheme, our host, and neither a code nor an error. Not something to
    // guess at.
    return OAuthCallback.Denied(error = "missing_code", description = null)
  }

  /**
   * Query parsing by hand rather than through `Uri.getQueryParameter`, so the
   * whole classifier runs in a JVM unit test. `android.net.Uri` is a framework
   * stub off device — the same trap `DataStoreSessionStore` documents for
   * `android.util.Base64`, except this one returns null instead of failing
   * loudly.
   */
  private fun queryOf(raw: String): Map<String, String> {
    val query = raw.substringAfter('?', missingDelimiterValue = "")
      // A fragment cannot legally follow the query in what GoTrue sends us, but
      // dropping it costs one call and stops a stray `#` from ending up inside
      // an authorization code.
      .substringBefore('#')

    if (query.isEmpty()) return emptyMap()

    return query.split("&")
      .mapNotNull { pair ->
        val name = pair.substringBefore('=')
        if (name.isEmpty()) return@mapNotNull null
        name to decode(pair.substringAfter('=', missingDelimiterValue = ""))
      }
      // First wins: a duplicated parameter is an attempt to confuse the reader,
      // and `toMap` would otherwise silently prefer the last one.
      .reversed()
      .toMap()
  }

  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

  private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}

/** What a redirect back into the app turned out to be. */
sealed interface OAuthCallback {

  /** Supabase issued an authorization code. It still has to be exchanged. */
  data class Granted(val code: String) : OAuthCallback

  /**
   * The round trip finished and there is no code. The user declined at Google's
   * prompt, or the provider refused.
   */
  data class Denied(val error: String, val description: String?) : OAuthCallback

  /** Not our redirect at all. The caller should ignore it. */
  data object NotACallback : OAuthCallback
}
