package cc.sunglint.weekclip.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * The session, sealed by [SecretCipher] and parked in a Preferences DataStore.
 *
 * DataStore rather than `SharedPreferences` because reads and writes here are
 * already suspend calls (the cipher touches the Keystore) and DataStore's API
 * is suspend-native; the alternative is `apply()` and a silent race on process
 * death.
 *
 * The stored value is one opaque base64 string. Nothing about the shape of a
 * session — how many tokens, when it expires, whose it is — is visible to
 * anything reading the preferences file.
 *
 * `java.util.Base64`, not `android.util.Base64`: the latter is a framework stub
 * in JVM unit tests and would quietly encode to `null`, so the whole store
 * would be untestable off-device for no gain. It is API 26+, and this app is
 * minSdk 28.
 */
class DataStoreSessionStore(
  private val dataStore: DataStore<Preferences>,
  private val cipher: SecretCipher,
  private val json: Json
) : SessionStore {

  override suspend fun read(): ProfileSession? {
    val encoded = dataStore.data.first()[SESSION_KEY] ?: return null

    val sealed = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
      ?: return discard()
    val plaintext = cipher.open(sealed) ?: return discard()

    return runCatching { json.decodeFromString<ProfileSession>(plaintext.decodeToString()) }
      .getOrNull()
      ?: discard()
  }

  override suspend fun write(session: ProfileSession) {
    val sealed = cipher.seal(json.encodeToString(session).encodeToByteArray())
    val encoded = Base64.getEncoder().encodeToString(sealed)
    dataStore.edit { it[SESSION_KEY] = encoded }
  }

  override suspend fun clear() {
    dataStore.edit { it.remove(SESSION_KEY) }
  }

  /**
   * Deletes a blob that cannot be turned back into a session and reports "no
   * session".
   *
   * Leaving it would mean paying the same failing decrypt on every launch
   * forever, and — worse — the next successful write would be the only thing
   * that ever cleared it. An unreadable session is indistinguishable from no
   * session to every caller, so it should be indistinguishable on disk too.
   */
  private suspend fun discard(): ProfileSession? {
    clear()
    return null
  }

  private companion object {
    val SESSION_KEY = stringPreferencesKey("profile_session_v1")
  }
}
