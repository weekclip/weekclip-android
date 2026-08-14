package cc.sunglint.weekclip

import cc.sunglint.weekclip.ui.navigation.WeekclipRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The route table is a contract with weekclip-web: PRD-0008 D4 sends
 * `/studios/:id/media/:mid`, `/invite/:token` and `/share/:token` into the app
 * via App Links. If a builder drifts from the pattern it fills, deep links
 * resolve to nothing — so both halves are asserted together.
 */
class WeekclipRoutesTest {

  @Test
  fun `media builder fills the media pattern`() {
    assertEquals("studios/{studioId}/media/{mediaId}", WeekclipRoutes.MEDIA)
    assertEquals("studios/s1/media/m1", WeekclipRoutes.media("s1", "m1"))
  }

  @Test
  fun `studio builder fills the studio pattern`() {
    assertEquals("studios/{studioId}", WeekclipRoutes.STUDIO)
    assertEquals("studios/s1", WeekclipRoutes.studio("s1"))
  }

  @Test
  fun `token routes match the web paths`() {
    assertEquals("invite/abc", WeekclipRoutes.invite("abc"))
    assertEquals("share/xyz", WeekclipRoutes.share("xyz"))
  }

  @Test
  fun `every builder produces a path its pattern matches`() {
    val cases = listOf(
      WeekclipRoutes.STUDIO to WeekclipRoutes.studio("s"),
      WeekclipRoutes.MEDIA to WeekclipRoutes.media("s", "m"),
      WeekclipRoutes.MEMBERS to WeekclipRoutes.members("s"),
      WeekclipRoutes.CAPACITY to WeekclipRoutes.capacity("s"),
      WeekclipRoutes.INVITE to WeekclipRoutes.invite("t"),
      WeekclipRoutes.SHARE to WeekclipRoutes.share("t")
    )
    cases.forEach { (pattern, concrete) ->
      assert(patternToRegex(pattern).matches(concrete)) {
        "'$concrete' does not match route pattern '$pattern'"
      }
    }
  }

  /** Turns `studios/{studioId}` into `^studios/[^/]+$`, escaping the literals. */
  private fun patternToRegex(pattern: String): Regex {
    val body = Regex("\\{[^}]+}")
      .split(pattern)
      .joinToString("[^/]+") { Regex.escape(it) }
    return Regex("^$body$")
  }
}
