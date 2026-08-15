package cc.sunglint.weekclip.core.session

import cc.sunglint.weekclip.BuildConfig

/**
 * Where the app talks to Supabase's auth service, and with which project key.
 *
 * Separate from `ApiEndpoints` because it points at a different party: those
 * are weekclip's own Workers, this is the identity provider that mints the
 * token they verify.
 *
 * ### The anon key is not a secret
 *
 * It is a JWT carrying the `anon` role, and weekclip's own production web
 * bundle serves it to every visitor — `https://weekclip.com/assets/index-*.js`
 * contains it in cleartext next to the production project URL (checked
 * 2026-08-15). Row-level security is the boundary; the key is an addressing
 * detail. Shipping it inside the APK is the same exposure the browser already
 * has, which is why it is a `buildConfigField` and not a runtime fetch.
 *
 * ### [isConfigured] is not defensive programming
 *
 * The production key is deliberately **blank** right now. Release builds have no
 * way to sign in at all — the only login the product offers is Google OAuth
 * (weekclip-web `LoginPage.tsx`), and that needs OAuth clients registered per
 * platform, which is the task tracked as 148.5c-b. Rather than commit a
 * production credential-shaped string that nothing can use yet, the refresher
 * asks this first and reports a clean sign-out instead of firing a request that
 * Supabase would reject with a confusing 401.
 */
data class AuthConfig(
  val supabaseUrl: String,
  val anonKey: String
) {
  val isConfigured: Boolean get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank()

  /**
   * Retrofit needs the trailing slash or it drops the last path segment —
   * `.../auth/v1` + `token` would resolve to `.../auth/token`. Same trap
   * `ApiEndpoints.apiBaseUrlForRetrofit` documents.
   */
  val tokenBaseUrl: String get() = supabaseUrl.trimEnd('/') + "/auth/v1/"

  companion object {
    fun fromBuildConfig(): AuthConfig = AuthConfig(
      supabaseUrl = BuildConfig.SUPABASE_URL,
      anonKey = BuildConfig.SUPABASE_ANON_KEY
    )
  }
}
