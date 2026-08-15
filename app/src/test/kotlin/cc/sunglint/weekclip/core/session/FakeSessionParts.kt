package cc.sunglint.weekclip.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared doubles for the session tests. Kept in one file so they stay honest. */

fun profileSession(
  accessToken: String = "access-1",
  refreshToken: String = "refresh-1",
  expiresAtEpochSeconds: Long = 10_000,
  userId: String = "user-1"
) = ProfileSession(accessToken, refreshToken, expiresAtEpochSeconds, userId)

class FixedClock(var nowEpochSeconds: Long = 0) : SessionClock {
  override fun nowEpochSeconds(): Long = nowEpochSeconds
}

/**
 * An in-memory [SessionStore] that also counts writes.
 *
 * The counts matter: several tests assert that a *failed* refresh did not
 * rewrite the store, which is invisible if you only look at the value.
 */
class RecordingSessionStore(private var stored: ProfileSession? = null) : SessionStore {
  var reads = 0
    private set
  var writes = 0
    private set
  var clears = 0
    private set

  override suspend fun read(): ProfileSession? {
    reads++
    return stored
  }

  override suspend fun write(session: ProfileSession) {
    writes++
    stored = session
  }

  override suspend fun clear() {
    clears++
    stored = null
  }

  fun peek(): ProfileSession? = stored
}

/**
 * A [SessionRefresher] whose outcome is scripted and whose calls are counted.
 *
 * [gate] lets a test hold a refresh open while other callers pile up behind the
 * manager's mutex — that is how single-flight is proven rather than asserted.
 */
class ScriptedRefresher(
  private val outcome: (ProfileSession) -> RefreshOutcome
) : SessionRefresher {
  var calls = 0
    private set

  val gate = Mutex()

  override suspend fun refresh(session: ProfileSession): RefreshOutcome {
    calls++
    gate.withLock { }
    return outcome(session)
  }
}

/**
 * The real `DataStore<Preferences>` interface, backed by memory.
 *
 * The alternative — `PreferenceDataStoreFactory` against a temp file — would be
 * testing androidx's file I/O, not [DataStoreSessionStore]'s handling of
 * base64, sealed bytes and JSON, which is where this project's bugs would be.
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
  private val state = MutableStateFlow(emptyPreferences())
  private val mutex = Mutex()

  override val data: Flow<Preferences> get() = state

  override suspend fun updateData(
    transform: suspend (t: Preferences) -> Preferences
  ): Preferences = mutex.withLock {
    val updated = transform(state.value)
    state.value = updated
    updated
  }
}

/**
 * A cipher that is honest about being reversible and nothing more.
 *
 * It prefixes a marker so [DataStoreSessionStore] can be handed a blob this
 * cipher did not produce, which is how the "key was invalidated" path is
 * reached without a device.
 */
class FakeCipher(private val marker: Byte = 0x7F) : SecretCipher {
  override fun seal(plaintext: ByteArray): ByteArray = byteArrayOf(marker) + plaintext

  override fun open(sealed: ByteArray): ByteArray? =
    if (sealed.isNotEmpty() && sealed[0] == marker) sealed.copyOfRange(1, sealed.size) else null
}
