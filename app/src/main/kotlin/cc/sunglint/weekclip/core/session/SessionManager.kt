package cc.sunglint.weekclip.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one thing that knows whether this app has a session, and the only thing
 * allowed to change that.
 *
 * ### Single-flight comes from the lock, not from a flag
 *
 * Every path in and out runs inside one [Mutex]. When the token goes stale, the
 * dashboard, a poster load and a prefetch can all discover it in the same
 * millisecond; without serialisation each fires its own refresh, and Supabase
 * **rotates the refresh token on use** — so the second and third refreshes
 * present a token that the first one just invalidated, and the user is signed
 * out by their own app. A hand-rolled `isRefreshing` boolean has to get the
 * same thing right with more moving parts.
 *
 * The cost is that a slow refresh blocks other callers. That is the intended
 * behaviour: they were about to send a request that could not have succeeded.
 */
@Singleton
class SessionManager @Inject constructor(
  private val store: SessionStore,
  private val refresher: SessionRefresher,
  private val authConfig: AuthConfig,
  private val clock: SessionClock
) {

  private val mutex = Mutex()

  /** Mirrors the store so the common path costs no disk read and no decrypt. */
  private var cached: ProfileSession? = null
  private var loadedFromStore = false

  val state: StateFlow<SessionState>
    field = MutableStateFlow<SessionState>(SessionState.Unknown)

  /**
   * The token to put on the next request, refreshing first if it is about to
   * expire.
   */
  suspend fun accessToken(): String? = mutex.withLock {
    val session = loadLocked() ?: return@withLock null
    if (session.isUsableAt(clock.nowEpochSeconds())) {
      session.accessToken
    } else {
      refreshLocked(session, keepOnFailure = true)?.accessToken
    }
  }

  /**
   * Called after the server has already answered 401 with [failedCredential].
   *
   * [failedCredential] is compared against what is stored, and that comparison
   * is the point: with several requests in flight, the first 401 triggers a
   * refresh and the rest arrive holding a token that is *already* superseded.
   * Without the check each of them would refresh again — the exact rotation
   * stampede the lock exists to prevent, just moved one layer out.
   */
  suspend fun accessTokenAfterUnauthorized(failedCredential: String?): String? = mutex.withLock {
    val session = loadLocked() ?: return@withLock null

    if (failedCredential != null && session.accessToken != failedCredential) {
      // Someone else already refreshed. Hand back what they got.
      return@withLock session.accessToken
    }

    refreshLocked(session, keepOnFailure = false)?.accessToken
  }

  /** Takes ownership of a freshly minted grant (sign-in). */
  suspend fun adopt(session: ProfileSession) = mutex.withLock {
    persistLocked(session)
  }

  suspend fun signOut() = mutex.withLock {
    clearLocked()
  }

  /** Forces the next read to go to disk. Exists for tests and for sign-out races. */
  suspend fun reload(): SessionState = mutex.withLock {
    loadedFromStore = false
    loadLocked()
    state.value
  }

  private suspend fun loadLocked(): ProfileSession? {
    if (!loadedFromStore) {
      cached = store.read()
      loadedFromStore = true
      publishLocked()
    }
    return cached
  }

  /**
   * @param keepOnFailure what to do when the refresh could not be *attempted*
   *   successfully. On the proactive path the answer is to hand back the old
   *   token anyway: the device clock is the only reason we believed it expired,
   *   and **the server is the authority on expiry**. A phone whose clock has
   *   drifted ten minutes fast would otherwise throw away a token that is still
   *   perfectly valid, and the 401 that comes back if it really has expired is
   *   handled one layer up. On the reactive path the server has already said
   *   401, so re-sending the same token is a guaranteed second failure.
   */
  private suspend fun refreshLocked(
    session: ProfileSession,
    keepOnFailure: Boolean
  ): ProfileSession? {
    if (!authConfig.isConfigured) {
      // No project key compiled in — nothing can renew this and nothing will.
      // Reporting a clean sign-out beats an endless loop of doomed requests.
      clearLocked()
      return null
    }

    return when (val outcome = refresher.refresh(session)) {
      is RefreshOutcome.Refreshed -> {
        persistLocked(outcome.session)
        outcome.session
      }

      RefreshOutcome.Rejected -> {
        clearLocked()
        null
      }

      RefreshOutcome.Unavailable -> if (keepOnFailure) session else null
    }
  }

  private suspend fun persistLocked(session: ProfileSession) {
    store.write(session)
    cached = session
    loadedFromStore = true
    publishLocked()
  }

  private suspend fun clearLocked() {
    store.clear()
    cached = null
    loadedFromStore = true
    publishLocked()
  }

  private fun publishLocked() {
    val session = cached
    state.value = if (session == null) SessionState.SignedOut else SessionState.SignedIn(session.userId)
  }
}
