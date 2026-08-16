package cc.sunglint.weekclip.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * The derivation, against the spec's own numbers.
 *
 * The first test is RFC 7636 Appendix B verbatim. That matters more than it
 * looks: every other test here could pass while the hash, the encoding or the
 * charset were all wrong together, because this class is the only thing that
 * produces *and* consumes them — a self-consistent mistake is invisible from
 * the inside. Appendix B is the outside.
 */
class PkceChallengeTest {

  @Test
  fun `derives the challenge from RFC 7636 Appendix B`() {
    val challenge = PkceChallenge.of("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

    assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge.challenge)
  }

  @Test
  fun `the method string is the lowercase one supabase-js sends`() {
    // Not cosmetic — GoTrue stores what /authorize was given and compares at
    // exchange time, so the wrong casing fails only after the user has finished
    // with Google.
    assertEquals("s256", PkceChallenge.METHOD)
  }

  @Test
  fun `a generated verifier is base64url with no padding and within the legal length`() {
    val verifier = PkceChallenge.generate().verifier

    // RFC 7636 §4.1: 43..128 characters from the unreserved set.
    assertTrue("length was ${verifier.length}", verifier.length in 43..128)
    assertTrue("not base64url: $verifier", verifier.matches(Regex("[A-Za-z0-9_-]+")))
  }

  @Test
  fun `two sign-ins do not share a verifier`() {
    // A reused verifier would let a code from one round trip be redeemed by
    // another. Cheap to assert, and the failure it guards against — someone
    // caching the challenge "for performance" — is entirely plausible.
    assertNotEquals(PkceChallenge.generate().verifier, PkceChallenge.generate().verifier)
  }

  @Test
  fun `the same verifier always derives the same challenge`() {
    val fixed = object : SecureRandom() {
      override fun nextBytes(bytes: ByteArray) {
        bytes.indices.forEach { bytes[it] = it.toByte() }
      }
    }

    assertEquals(PkceChallenge.generate(fixed), PkceChallenge.generate(fixed))
  }
}
