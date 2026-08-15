package cc.sunglint.weekclip.core.session

import kotlinx.serialization.Serializable

/**
 * A signed-in profile's Supabase session, exactly as it is persisted.
 *
 * "Session" is not an abstraction this app invented: weekclip-api verifies a
 * **Supabase access token** against the project's remote JWKS
 * (`supabase-token-verifier.ts`), so whatever the app stores has to be the real
 * Supabase grant — access token, refresh token, and the absolute expiry the
 * grant came with.
 *
 * Measured against the dev project (2026-08-15): a password grant returns
 * `expires_in: 3600` and an absolute `expires_at` in **epoch seconds**. The
 * absolute value is the one kept — `expires_in` is relative to a clock this app
 * does not own, and re-deriving it from device time on every read would make
 * the stored session drift every time the phone's clock did.
 *
 * This is the *profile* axis. The guest/share axis is a different credential
 * with a different lifecycle — see [cc.sunglint.weekclip.core.network.SessionCredentialProvider].
 */
@Serializable
data class ProfileSession(
  val accessToken: String,
  val refreshToken: String,
  val expiresAtEpochSeconds: Long,
  val userId: String
) {
  /**
   * Whether this token can still be put on a request at [nowEpochSeconds].
   *
   * [skewSeconds] is subtracted deliberately. A token with three seconds of
   * life left is not usable: the request still has to be built, resolve DNS,
   * complete a TLS handshake and cross a mobile link, and it is validated on
   * arrival, not on departure. Refreshing slightly early costs one extra call
   * an hour; refreshing slightly late costs a 401 on a screen the user is
   * looking at.
   */
  fun isUsableAt(nowEpochSeconds: Long, skewSeconds: Long = REFRESH_SKEW_SECONDS): Boolean =
    nowEpochSeconds + skewSeconds < expiresAtEpochSeconds

  companion object {
    /** 60s against a 3600s token: 1.7% of its life spent avoiding a race. */
    const val REFRESH_SKEW_SECONDS: Long = 60
  }
}
