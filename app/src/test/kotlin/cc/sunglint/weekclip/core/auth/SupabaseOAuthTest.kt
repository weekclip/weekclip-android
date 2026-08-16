package cc.sunglint.weekclip.core.auth

import cc.sunglint.weekclip.core.session.AuthConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire contract of the round trip, both directions.
 *
 * Every assertion here is about a string that a *different party* reads —
 * GoTrue for the outbound URL, the OS for the inbound one — which is exactly
 * the class of thing that cannot be checked by running the app: a wrong
 * parameter name produces a browser that opens, a prompt that completes, and a
 * failure only at the end.
 */
class SupabaseOAuthTest {

  private val config = AuthConfig(
    supabaseUrl = "https://project.supabase.co",
    anonKey = "anon-key"
  )
  private val challenge = PkceChallenge.of("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

  @Test
  fun `the authorize url carries the four parameters GoTrue needs`() {
    val url = SupabaseOAuth.authorizeUrl(config, challenge)!!

    assertTrue(url, url.startsWith("https://project.supabase.co/auth/v1/authorize?"))
    assertTrue(url, url.contains("provider=google"))
    assertTrue(url, url.contains("code_challenge=${challenge.challenge}"))
    assertTrue(url, url.contains("code_challenge_method=s256"))
  }

  @Test
  fun `the redirect is percent-encoded, scheme and all`() {
    val url = SupabaseOAuth.authorizeUrl(config, challenge)!!

    // Unencoded, the `://` would terminate the parameter and GoTrue would
    // redirect to the site URL instead — the silent failure this whole flow's
    // worst bug is made of.
    assertTrue(url, url.contains("redirect_to=cc.sunglint.weekclip%3A%2F%2Fauth-callback"))
  }

  @Test
  fun `the verifier never leaves the device`() {
    val url = SupabaseOAuth.authorizeUrl(config, challenge)!!

    // The entire point of S256. If this ever fails, PKCE has become decoration.
    assertTrue(url, !url.contains(challenge.verifier))
  }

  @Test
  fun `a build with no project key has no url to open`() {
    val url = SupabaseOAuth.authorizeUrl(
      config = AuthConfig(supabaseUrl = "https://project.supabase.co", anonKey = ""),
      challenge = challenge
    )

    assertNull(url)
  }

  @Test
  fun `a redirect carrying a code is a grant`() {
    val callback = SupabaseOAuth.parseCallback(
      "cc.sunglint.weekclip://auth-callback?code=1e0a4d6b-af2e-4a9e-8f2a-000000000001"
    )

    assertEquals(OAuthCallback.Granted("1e0a4d6b-af2e-4a9e-8f2a-000000000001"), callback)
  }

  @Test
  fun `a refusal is read from either spelling of the error field`() {
    // OAuth says `error`; GoTrue sends `error_code`. Which one arrives depends
    // on where in the chain the refusal happened, so both are read.
    assertEquals(
      OAuthCallback.Denied("access_denied", "The user did not approve"),
      SupabaseOAuth.parseCallback(
        "cc.sunglint.weekclip://auth-callback" +
          "?error=access_denied&error_description=The+user+did+not+approve"
      )
    )
    assertEquals(
      OAuthCallback.Denied("provider_email_needs_verification", null),
      SupabaseOAuth.parseCallback(
        "cc.sunglint.weekclip://auth-callback?error_code=provider_email_needs_verification"
      )
    )
  }

  @Test
  fun `our own scheme with neither a code nor an error is not treated as success`() {
    val callback = SupabaseOAuth.parseCallback("cc.sunglint.weekclip://auth-callback?state=x")

    assertEquals(OAuthCallback.Denied("missing_code", null), callback)
  }

  @Test
  fun `an app link is not a callback`() {
    // MainActivity hands every incoming intent to this, so a share link must
    // come back as "not mine" rather than as a broken sign-in. A `code` query
    // on a weekclip.com URL must not be mistaken for our redirect either.
    assertEquals(
      OAuthCallback.NotACallback,
      SupabaseOAuth.parseCallback("https://weekclip.com/share/abc")
    )
    assertEquals(
      OAuthCallback.NotACallback,
      SupabaseOAuth.parseCallback("https://weekclip.com/invite/abc?code=deadbeef")
    )
  }

  @Test
  fun `a different host on our scheme is not a callback`() {
    // Leaves room for a second private-use route later without it silently
    // being read as a spent sign-in.
    assertEquals(
      OAuthCallback.NotACallback,
      SupabaseOAuth.parseCallback("cc.sunglint.weekclip://something-else?code=x")
    )
  }

  @Test
  fun `a duplicated parameter cannot smuggle a second code past the first`() {
    val callback = SupabaseOAuth.parseCallback(
      "cc.sunglint.weekclip://auth-callback?code=real&code=injected"
    )

    assertEquals(OAuthCallback.Granted("real"), callback)
  }
}
