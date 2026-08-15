package cc.sunglint.weekclip.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which routes belong to which credential.
 *
 * The paths are taken from weekclip-api's route table (`src/platform/app.ts`,
 * lines 437-446), not invented. The pair that matters is the last two: both
 * contain "share", and they sit on **opposite** axes.
 */
class SessionAxisTest {

  @Test
  fun `ordinary api routes are on the profile axis`() {
    listOf(
      "/api/v1/studios",
      "/api/v1/studios/abc/media",
      "/api/v1/studios/abc/media/def",
      "/api/v1/auth/session"
    ).forEach {
      assertEquals(it, SessionAxis.Profile, SessionAxis.of(it))
    }
  }

  @Test
  fun `the guest share surface is on the guest axis`() {
    listOf(
      "/api/v1/share/tok/session",
      "/api/v1/share/tok/media",
      "/api/v1/share/tok/media/mid",
      "/api/v1/share/tok/media/mid/original"
    ).forEach {
      assertEquals(it, SessionAxis.Guest, SessionAxis.of(it))
    }
  }

  @Test
  fun `owner-side share-link management stays on the profile axis`() {
    // `/api/v1/studios/:id/share-links` is authenticated as the owner. A
    // substring match on "share" would strip the bearer from the screen that
    // creates and revokes share links — i.e. break the owner feature while
    // trying to support the guest one.
    listOf(
      "/api/v1/studios/abc/share-links",
      "/api/v1/studios/abc/share-links/xyz"
    ).forEach {
      assertEquals(it, SessionAxis.Profile, SessionAxis.of(it))
    }
  }

  @Test
  fun `a future api version keeps the same split`() {
    assertEquals(SessionAxis.Guest, SessionAxis.of("/api/v2/share/tok/media"))
    assertEquals(SessionAxis.Profile, SessionAxis.of("/api/v2/studios"))
  }
}
