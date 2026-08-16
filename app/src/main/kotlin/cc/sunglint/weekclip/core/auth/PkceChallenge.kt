package cc.sunglint.weekclip.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * A PKCE (RFC 7636) verifier and the challenge derived from it.
 *
 * ### Why a public client needs this at all
 *
 * The authorization code comes back to the app over `cc.sunglint.weekclip://`,
 * and **Android will hand that redirect to any app that claims the same custom
 * scheme**. There is no registry stopping a second app from declaring it. So
 * the code alone cannot be trusted to identify us: PKCE binds it to a secret
 * this process generated and never sent over the redirect, and the exchange
 * fails for anyone who intercepted only the code.
 *
 * This is exactly the reason a native app cannot use the implicit flow the web
 * uses — there the token lands in a URL fragment that the interceptor would
 * simply read.
 *
 * ### `S256`, not `plain`
 *
 * `plain` sends the verifier itself as the challenge, which puts it in the
 * outbound URL and defeats the point. GoTrue accepts both; only one of them is
 * worth having.
 *
 * ⚠️ The method string is lowercase **`s256`**, not the `S256` spelled in
 * RFC 7636 §4.3. That is not a guess: `@supabase/auth-js`'s
 * `getCodeChallengeAndMethod` returns the literal `'s256'`, and that library is
 * what weekclip-web already uses against these same Supabase projects. Copying
 * the RFC's casing would be a change nothing here has evidence for, and the
 * place it would surface is the exchange — after the user has already finished
 * the Google round trip.
 */
data class PkceChallenge(val verifier: String, val challenge: String) {

  companion object {
    /** What `code_challenge_method` has to say. See the class note on casing. */
    const val METHOD = "s256"

    /**
     * 32 bytes of entropy, base64url-encoded to 43 characters — the middle of
     * RFC 7636 §4.1's 43..128 range, and the length every reference
     * implementation uses.
     *
     * [random] is a parameter so a test can pin the output. Nothing in the app
     * passes it.
     */
    fun generate(random: SecureRandom = SecureRandom()): PkceChallenge =
      of(ENCODER.encodeToString(ByteArray(VERIFIER_BYTES).also(random::nextBytes)))

    /** Derives the challenge for an already-chosen [verifier]. */
    fun of(verifier: String): PkceChallenge = PkceChallenge(
      verifier = verifier,
      challenge = ENCODER.encodeToString(
        MessageDigest.getInstance("SHA-256")
          // US-ASCII, not the platform default: RFC 7636 §4.2 hashes the ASCII
          // octets of the verifier, and the verifier is base64url so it has no
          // characters outside ASCII anyway. Stating the charset means a device
          // with an unusual default locale cannot change the hash.
          .digest(verifier.toByteArray(Charsets.US_ASCII))
      )
    )

    private const val VERIFIER_BYTES = 32

    /**
     * `java.util.Base64`, not `android.util.Base64` — the same reason
     * `DataStoreSessionStore` gives: the framework class is a stub in JVM unit
     * tests and would silently encode to `null`, making this untestable off
     * device. API 26+, and this app is minSdk 28.
     *
     * Padding is dropped because base64**url** with `=` is not URL-safe in a
     * query string without further escaping, and RFC 7636 §4.1 excludes it.
     */
    private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
  }
}
