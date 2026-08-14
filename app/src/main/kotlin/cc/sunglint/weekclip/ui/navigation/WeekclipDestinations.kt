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
}
