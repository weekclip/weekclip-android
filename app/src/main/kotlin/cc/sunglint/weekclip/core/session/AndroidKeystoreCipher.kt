package cc.sunglint.weekclip.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * AES-256/GCM under a key that lives in `AndroidKeyStore` and never leaves it.
 *
 * ### Why this and not `EncryptedSharedPreferences`
 *
 * The version catalog carried `androidx.security:security-crypto:1.1.0`, picked
 * on 2026-08-14 as "the latest stable release". It is stable, and it is also the
 * release in which **the entire public API was deprecated** — `MasterKey`,
 * `MasterKeys`, `EncryptedSharedPreferences` and `EncryptedFile` all carry
 * `@Deprecated` in the 1.1.0 sources. The javadoc says what to do instead, and
 * this class is that instruction taken literally:
 *
 * > `MasterKey` — *"Use `javax.crypto.KeyGenerator` with AndroidKeyStore
 * > instance instead."*
 *
 * Adopting a wholly-deprecated library on day one, for the one file that holds
 * credentials, would have been debt with a known due date. The dependency is
 * gone from the catalog.
 *
 * ### Two decisions worth keeping
 *
 * **`setUserAuthenticationRequired(false)`** — PRD-0008 D8 requires uploads that
 * keep running while the app is backgrounded, and an upload has to re-authorise
 * itself. A key that demands a biometric prompt cannot be used by a
 * `WorkManager` job at 3am, which is precisely when a large upload is still
 * going. Same reason `setUnlockedDeviceRequired(true)` is absent: the phone in
 * a pocket is a locked phone.
 *
 * **A random IV per seal, taken from the `Cipher`** — GCM catastrophically
 * loses confidentiality *and* authenticity if an IV is ever reused under the
 * same key, and the platform's generated IV is the one path where that cannot
 * be got wrong by hand.
 */
class AndroidKeystoreCipher(
  private val alias: String = DEFAULT_ALIAS
) : SecretCipher {

  override fun seal(plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
    val iv = cipher.iv
    check(iv.size == IV_LENGTH) { "expected a $IV_LENGTH-byte GCM IV, got ${iv.size}" }
    return iv + cipher.doFinal(plaintext)
  }

  override fun open(sealed: ByteArray): ByteArray? {
    if (sealed.size <= IV_LENGTH) {
      // Not even a full IV, so there is no ciphertext to authenticate.
      return null
    }
    val key = existingKey() ?: return null
    return try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(
        Cipher.DECRYPT_MODE,
        key,
        GCMParameterSpec(TAG_LENGTH_BITS, sealed, 0, IV_LENGTH)
      )
      cipher.doFinal(sealed, IV_LENGTH, sealed.size - IV_LENGTH)
    } catch (e: GeneralSecurityException) {
      // Covers the three ways this legitimately fails, all of which mean the
      // same thing to the caller — the session is gone:
      //   · AEADBadTagException          — blob tampered with or truncated
      //   · KeyPermanentlyInvalidatedException — secure lock screen removed
      //   · InvalidKeyException          — key replaced since the seal
      null
    }
  }

  private fun obtainKey(): SecretKey = existingKey() ?: generateKey()

  private fun existingKey(): SecretKey? {
    val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
    return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
  }

  private fun generateKey(): SecretKey {
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
    generator.init(
      KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KEY_SIZE_BITS)
        .setUserAuthenticationRequired(false)
        .build()
    )
    return generator.generateKey()
  }

  companion object {
    const val DEFAULT_ALIAS: String = "cc.sunglint.weekclip.session"

    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BITS = 256
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
  }
}
