package cc.sunglint.weekclip.ui

import cc.sunglint.weekclip.ui.navigation.WeekclipDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The paths weekclip.com claims for this app, and the ones it must not.
 *
 * The claimed set lives in weekclip-web under `public/.well-known` and is asserted
 * there too. Both sides have to agree: a claimed link the app cannot parse
 * opens the app and dead-ends there, which is worse than opening the browser.
 *
 * Only the `String` overload is exercised. The `Uri` one is a one-line
 * delegation to `Uri.path`, and `android.net.Uri` is a framework stub in a JVM
 * unit test — testing it here would assert against a stub returning null, which
 * is how a test ends up proving nothing.
 */
class WeekclipDeepLinkTest {

  @Test
  fun `every claimed path resolves to the route it names`() {
    assertEquals("dashboard", WeekclipDeepLink.routeOf("/dashboard"))
    assertEquals("studios/st_1", WeekclipDeepLink.routeOf("/studios/st_1"))
    assertEquals("studios/st_1/media/md_2", WeekclipDeepLink.routeOf("/studios/st_1/media/md_2"))
    assertEquals("studios/st_1/members", WeekclipDeepLink.routeOf("/studios/st_1/members"))
    assertEquals("studios/st_1/capacity", WeekclipDeepLink.routeOf("/studios/st_1/capacity"))
    assertEquals("invite/tok", WeekclipDeepLink.routeOf("/invite/tok"))
    assertEquals("share/tok", WeekclipDeepLink.routeOf("/share/tok"))
  }

  @Test
  fun `a trailing slash does not change the route`() {
    assertEquals("studios/st_1", WeekclipDeepLink.routeOf("/studios/st_1/"))
  }

  @Test
  fun `paths this app does not own return null rather than a guess`() {
    // PRD-0008 D4 keeps these on the web on purpose: the store review needs a
    // privacy-policy URL, and #173/#181's SEO assets hang off the landing and
    // policy pages. D3 keeps /billing out of the app entirely.
    listOf(
      "/",
      "/login",
      "/sign-up",
      "/policies/privacy",
      "/settings/profile",
      "/settings/studios",
      "/billing/products",
      "/billing/bridge",
      "/architecture",
      "/studios",
      "/studios/st_1/unknown",
      "/studios/st_1/media",
      "/studios/st_1/media/md_2/extra"
    ).forEach { path ->
      assertNull("$path must not resolve", WeekclipDeepLink.routeOf(path))
    }
  }

  @Test
  fun `the routes match what the navigation graph registers`() {
    // The graph's patterns are `studios/{studioId}` etc.; what this produces has
    // to be the same string with the placeholders filled, or `navigate()` will
    // not find a destination.
    assertEquals(
      WeekclipDeepLink.routeOf("/studios/a/media/b"),
      cc.sunglint.weekclip.ui.navigation.WeekclipRoutes.media("a", "b")
    )
  }
}
