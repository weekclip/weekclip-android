package cc.sunglint.weekclip.core.session

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the store does with a blob it cannot turn back into a session.
 *
 * The happy path is one test; the other four are the ones that decide whether a
 * user gets a login screen or a crash loop. Every one of them is reachable on a
 * real phone — a removed lock screen invalidates the Keystore key, and a
 * half-finished write leaves a truncated blob.
 */
class DataStoreSessionStoreTest {

  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private val sessionKey = stringPreferencesKey("profile_session_v1")

  private fun store(
    dataStore: InMemoryPreferencesDataStore,
    cipher: SecretCipher = FakeCipher()
  ) = DataStoreSessionStore(dataStore, cipher, json)

  @Test
  fun `a written session comes back identical`() = runTest {
    val dataStore = InMemoryPreferencesDataStore()
    val store = store(dataStore)
    val session = profileSession(accessToken = "a", refreshToken = "r", expiresAtEpochSeconds = 42, userId = "u")

    store.write(session)

    assertEquals(session, store.read())
  }

  @Test
  fun `nothing stored reads as no session`() = runTest {
    assertNull(store(InMemoryPreferencesDataStore()).read())
  }

  @Test
  fun `the persisted value reveals nothing about the session`() = runTest {
    val dataStore = InMemoryPreferencesDataStore()
    store(dataStore).write(profileSession(accessToken = "super-secret-token", userId = "user-1"))

    val persisted = dataStore.data.first()[sessionKey]!!

    assertTrue("the token was stored in the clear", !persisted.contains("super-secret-token"))
    assertTrue("the user id was stored in the clear", !persisted.contains("user-1"))
  }

  @Test
  fun `a blob sealed under a key that no longer exists reads as no session and is deleted`() = runTest {
    // What a phone does after the user removes their lock screen: the Keystore
    // key is invalidated, so the bytes on disk can never be opened again.
    val dataStore = InMemoryPreferencesDataStore()
    store(dataStore, FakeCipher(marker = 0x01)).write(profileSession())

    val afterKeyChange = store(dataStore, FakeCipher(marker = 0x02))

    assertNull(afterKeyChange.read())
    assertNull(
      "an unreadable blob must not be left to fail on every launch forever",
      dataStore.data.first()[sessionKey]
    )
  }

  @Test
  fun `a value that is not base64 reads as no session and is deleted`() = runTest {
    val dataStore = InMemoryPreferencesDataStore()
    dataStore.edit { it[sessionKey] = "!!! not base64 !!!" }

    assertNull(store(dataStore).read())
    assertNull(dataStore.data.first()[sessionKey])
  }

  @Test
  fun `a decryptable blob that is not a session reads as no session and is deleted`() = runTest {
    val dataStore = InMemoryPreferencesDataStore()
    val cipher = FakeCipher()
    dataStore.edit {
      it[sessionKey] = java.util.Base64.getEncoder()
        .encodeToString(cipher.seal("""{"unexpected":"shape"}""".encodeToByteArray()))
    }

    assertNull(store(dataStore, cipher).read())
    assertNull(dataStore.data.first()[sessionKey])
  }

  @Test
  fun `clear removes the stored session`() = runTest {
    val dataStore = InMemoryPreferencesDataStore()
    val store = store(dataStore)
    store.write(profileSession())

    store.clear()

    assertNull(store.read())
    assertNull(dataStore.data.first()[sessionKey])
  }
}
