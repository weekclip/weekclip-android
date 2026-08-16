package cc.sunglint.weekclip.ui.navigation

/**
 * Route table.
 *
 * These paths intentionally mirror weekclip-web's URLs one-for-one. That is not
 * cosmetic: PRD-0008 D4 routes `/studios/:id/media/:mid`, `/invite/:token` and
 * `/share/:token` into the app via App Links, and PRD-0007 D6 named the media
 * route as "the boundary where native push/pop attaches". If these drift from
 * the web paths, deep links stop resolving.
 */
object WeekclipRoutes {
  const val DASHBOARD = "dashboard"

  const val STUDIO = "studios/{studioId}"
  const val MEDIA = "studios/{studioId}/media/{mediaId}"
  const val MEMBERS = "studios/{studioId}/members"
  const val CAPACITY = "studios/{studioId}/capacity"
  const val INVITE = "invite/{token}"
  const val SHARE = "share/{token}"

  const val ARG_STUDIO_ID = "studioId"
  const val ARG_MEDIA_ID = "mediaId"
  const val ARG_TOKEN = "token"

  fun studio(studioId: String): String = "studios/$studioId"

  fun media(studioId: String, mediaId: String): String = "studios/$studioId/media/$mediaId"

  fun members(studioId: String): String = "studios/$studioId/members"

  fun capacity(studioId: String): String = "studios/$studioId/capacity"

  fun invite(token: String): String = "invite/$token"

  fun share(token: String): String = "share/$token"

  /**
   * Whether this route can be opened with no account at all.
   *
   * The single exception PRD-0008 D4 carves out of "everything needs a
   * profile", and it is drawn to match the **API**, not a UI preference:
   * `SessionAxis` routes the versioned API's `share` subtree to the guest
   * credential — the share link's own HMAC — and everything else to the profile
   * JWT. A screen the gate lets through that then calls a profile endpoint
   * would render a 401, so these two definitions have to agree.
   * `SessionAxisTest` pins the other half.
   *
   * (The exact path pattern is spelled out in `SessionAxis` and deliberately
   * not repeated here: a glob written in a KDoc contains a star followed by a
   * slash, which **closes the comment early** — Kotlin's block comments nest,
   * and the compiler then reports the damage at the end of the file rather than
   * at the doc that caused it. This repo has now hit that three times.)
   *
   * `invite/` is deliberately **not** here. Accepting an invite attaches a
   * studio to an account, so the account has to exist first — the same
   * conclusion weekclip-web reaches in flow F3.
   */
  fun isGuestRoute(route: String): Boolean = route.startsWith("share/")
}
