package cc.sunglint.weekclip.core.network

import cc.sunglint.weekclip.BuildConfig

/**
 * Service base URLs.
 *
 * weekclip is split across three Workers services and the app talks to two of
 * them, exactly as the web client does (`getApiBaseUrl` / `getUserApiBaseUrl`
 * in weekclip-web `src/shared/api/client.ts`).
 *
 * Billing is deliberately absent: PRD-0008 D3 requires zero payment strings in
 * the app binary, and N6 makes CI check for it. Capacity *reads* that the app
 * does need are served by weekclip-api, not the billing service.
 *
 * The values come from `BuildConfig`, which `app/build.gradle.kts` fills per
 * build type. They are not defaulted here: a constant in this file would be the
 * same in a debug build and a release one, and the two tiers are separate
 * Cloudflare accounts with separate databases.
 */
data class ApiEndpoints(
  val apiBaseUrl: String,
  val userApiBaseUrl: String
) {
  init {
    // Retrofit's own failure for an empty base URL is an IllegalArgumentException
    // from deep inside HttpUrl, which says nothing about why. The previous
    // skeleton shipped `UNCONFIGURED = ApiEndpoints("", "")` and would have hit
    // exactly that on the first request.
    require(apiBaseUrl.isNotBlank()) { "apiBaseUrl is blank — check buildConfigField in app/build.gradle.kts" }
    require(userApiBaseUrl.isNotBlank()) { "userApiBaseUrl is blank — check buildConfigField in app/build.gradle.kts" }
  }

  /**
   * Retrofit requires a trailing slash on the base URL, or it silently drops
   * the last path segment: `.../api/v1` + `studios` resolves to `.../api/studios`.
   * The web client stores these without the slash (it concatenates by hand), so
   * normalising here keeps both sides reading the same value.
   */
  val apiBaseUrlForRetrofit: String get() = apiBaseUrl.trimEnd('/') + "/"

  companion object {
    fun fromBuildConfig(): ApiEndpoints = ApiEndpoints(
      apiBaseUrl = BuildConfig.API_BASE_URL,
      userApiBaseUrl = BuildConfig.USER_API_BASE_URL
    )
  }
}
