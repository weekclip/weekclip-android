package cc.sunglint.weekclip.ui.navigation

import android.net.Uri

/**
 * Turns an incoming link into a route this app owns, or nothing.
 *
 * The mirror of `WeekclipRoute(url:)` in weekclip-ios, and it has to stay a
 * mirror: weekclip.com's `apple-app-site-association` and `assetlinks.json`
 * claim exactly `/dashboard` plus everything under `/studios`, `/invite` and
 * `/share`. A claimed link the app cannot parse **opens the app and dead-ends
 * there** — strictly worse than opening the browser.
 *
 * Returning null means "not a route this app owns". Those go to the web rather
 * than being guessed at: PRD-0008 D4 keeps landing, policy and pre-login pages
 * on the web on purpose, because the store review needs a privacy-policy URL
 * and #173/#181's SEO assets hang off them.
 *
 * ⚠️ **Nothing calls this yet.** The `AndroidManifest` intent-filters that would
 * are held back until weekclip-android has a release signing key (task 148.4b):
 * Android App Links verify against the signing certificate, so declaring
 * `autoVerify` before `assetlinks.json` carries a fingerprint gets links that
 * quietly never open the app. This half is the half that can be built and
 * tested without the key, and iOS has had its equivalent since the skeleton.
 */
object WeekclipDeepLink {
  // ⚠️ Kotlin block comments NEST, unlike Java's. A slash-star sequence inside a
  // KDoc — which is what writing a glob like the association files use produces
  // — opens a comment that never closes, and the compiler reports
  // "Unclosed comment" at the end of the file rather than at the doc that
  // caused it. That is why the patterns are spelled out in prose above.


  /**
   * @param uri a full link (`https://weekclip.com/studios/a/media/b`) or a bare
   *   path. Only the path is read — host verification is the platform's job,
   *   via the association files weekclip.com serves (PRD-0008 N2).
   */
  fun routeOf(uri: Uri): String? = routeOf(uri.path ?: return null)

  fun routeOf(path: String): String? {
    val segments = path.split("/").filter { it.isNotEmpty() }

    return when {
      segments.size == 1 && segments[0] == "dashboard" ->
        WeekclipRoutes.DASHBOARD

      segments.size == 2 && segments[0] == "studios" ->
        WeekclipRoutes.studio(segments[1])

      segments.size == 2 && segments[0] == "invite" ->
        WeekclipRoutes.invite(segments[1])

      segments.size == 2 && segments[0] == "share" ->
        WeekclipRoutes.share(segments[1])

      segments.size == 3 && segments[0] == "studios" && segments[2] == "members" ->
        WeekclipRoutes.members(segments[1])

      segments.size == 3 && segments[0] == "studios" && segments[2] == "capacity" ->
        WeekclipRoutes.capacity(segments[1])

      segments.size == 4 && segments[0] == "studios" && segments[2] == "media" ->
        WeekclipRoutes.media(segments[1], segments[3])

      else -> null
    }
  }
}
