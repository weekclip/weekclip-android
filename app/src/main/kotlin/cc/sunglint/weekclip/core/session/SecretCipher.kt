package cc.sunglint.weekclip.core.session

/**
 * Authenticated encryption for whatever the app has to keep on disk.
 *
 * A seam with two jobs. The obvious one is swapping the implementation. The
 * load-bearing one is **testability**: the real implementation talks to
 * `AndroidKeyStore`, which does not exist in a JVM unit test, and this project's
 * CI runs unit tests on ubuntu with no device (`.github/workflows/ci.yml`). With
 * the cipher behind an interface, every branch of [SessionStore] — round-trip,
 * corrupt blob, wrong key — is exercised by `testDebugUnitTest`, and only the
 * ~40 lines that call the Keystore need a device.
 *
 * That asymmetry is worth stating plainly: on iOS the equivalent test runs
 * against the **real** Keychain because its test bundle is app-hosted on a
 * simulator. Android cannot match that in CI today.
 */
interface SecretCipher {
  /** Returns `iv || ciphertext`, self-contained and safe to store as one blob. */
  fun seal(plaintext: ByteArray): ByteArray

  /**
   * Reverses [seal], or returns `null` if the blob cannot be authenticated —
   * truncated, tampered with, or sealed under a key that no longer exists.
   * Never throws: an unreadable session is a sign-out, not a crash.
   */
  fun open(sealed: ByteArray): ByteArray?
}
