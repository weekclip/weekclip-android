package cc.sunglint.weekclip.core.session

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rules that decide whether a user stays signed in.
 *
 * Every case here is one the app will actually meet: an hourly rotation, a
 * phone in a lift, a revoked account, several screens loading at once. The
 * expiry arithmetic is driven by [FixedClock] rather than by sleeping, which is
 * the only reason a test for "the token expired an hour in" finishes in
 * milliseconds.
 */
class SessionManagerTest {

  private val configured = AuthConfig(
    supabaseUrl = "https://project.supabase.co",
    anonKey = "anon-key"
  )

  private fun manager(
    store: SessionStore,
    refresher: SessionRefresher,
    clock: SessionClock,
    authConfig: AuthConfig = configured
  ) = SessionManager(store, refresher, authConfig, clock)

  @Test
  fun `a token with life left is handed out without touching the network`() = runTest {
    val store = RecordingSessionStore(profileSession(expiresAtEpochSeconds = 10_000))
    val refresher = ScriptedRefresher { error("must not refresh") }

    val token = manager(store, refresher, FixedClock(nowEpochSeconds = 5_000)).accessToken()

    assertEquals("access-1", token)
    assertEquals(0, refresher.calls)
  }

  @Test
  fun `a token inside the skew window is refreshed before it is used`() = runTest {
    // 30s of life left, and the skew is 60s: still valid by the clock, but not
    // valid for long enough to survive the round trip it is about to make.
    val store = RecordingSessionStore(profileSession(expiresAtEpochSeconds = 10_000))
    val refresher = ScriptedRefresher {
      RefreshOutcome.Refreshed(profileSession(accessToken = "access-2", expiresAtEpochSeconds = 13_570))
    }

    val token = manager(store, refresher, FixedClock(nowEpochSeconds = 9_970)).accessToken()

    assertEquals("access-2", token)
    assertEquals(1, refresher.calls)
    assertEquals("access-2", store.peek()?.accessToken)
  }

  @Test
  fun `concurrent callers that all find a stale token cause exactly one refresh`() = runTest {
    // Supabase rotates the refresh token on use, so a second concurrent refresh
    // presents a token the first one has already invalidated — and signs the
    // user out with their own app.
    val store = RecordingSessionStore(profileSession(expiresAtEpochSeconds = 100))
    val refresher = ScriptedRefresher {
      RefreshOutcome.Refreshed(profileSession(accessToken = "access-2", expiresAtEpochSeconds = 3_700))
    }
    val manager = manager(store, refresher, FixedClock(nowEpochSeconds = 90))

    refresher.gate.lock()
    val callers = List(8) { async { manager.accessToken() } }
    // Let every caller reach the lock before the first refresh is allowed to
    // finish; without this the first could complete before the rest even start.
    repeat(20) { yield() }
    refresher.gate.unlock()

    assertEquals(List(8) { "access-2" }, callers.map { it.await() })
    assertEquals(1, refresher.calls)
  }

  @Test
  fun `a refresh that cannot reach the network keeps the session`() = runTest {
    // The device clock is the only reason we believe this expired. The server
    // decides, and it has not been asked yet.
    val store = RecordingSessionStore(profileSession(expiresAtEpochSeconds = 100))
    val refresher = ScriptedRefresher { RefreshOutcome.Unavailable }

    val token = manager(store, refresher, FixedClock(nowEpochSeconds = 90)).accessToken()

    assertEquals("access-1", token)
    assertNotNull(store.peek())
    assertEquals(0, store.clears)
  }

  @Test
  fun `a refresh the provider refuses signs the user out`() = runTest {
    val store = RecordingSessionStore(profileSession(expiresAtEpochSeconds = 100))
    val refresher = ScriptedRefresher { RefreshOutcome.Rejected }
    val manager = manager(store, refresher, FixedClock(nowEpochSeconds = 90))

    assertNull(manager.accessToken())
    assertNull(store.peek())
    assertEquals(SessionState.SignedOut, manager.state.value)
  }

  @Test
  fun `after a 401 the same token is not handed back on an unreachable network`() = runTest {
    // Mirror image of the case above: here the server HAS spoken, and re-sending
    // what it just rejected is a guaranteed second failure.
    val store = RecordingSessionStore(profileSession(expiresAtEpochSeconds = 10_000))
    val refresher = ScriptedRefresher { RefreshOutcome.Unavailable }
    val manager = manager(store, refresher, FixedClock(nowEpochSeconds = 0))

    assertNull(manager.accessTokenAfterUnauthorized(failedCredential = "access-1"))
    assertNotNull(store.peek())
  }

  @Test
  fun `a 401 carrying a superseded token does not trigger a second refresh`() = runTest {
    // Several requests were in flight when the token rotated. The stragglers
    // come back 401 holding the OLD token; refreshing again for each of them is
    // the rotation stampede, one layer out from the mutex.
    val store = RecordingSessionStore(profileSession(accessToken = "access-2"))
    val refresher = ScriptedRefresher { error("must not refresh") }
    val manager = manager(store, refresher, FixedClock(nowEpochSeconds = 0))

    val token = manager.accessTokenAfterUnauthorized(failedCredential = "access-1")

    assertEquals("access-2", token)
    assertEquals(0, refresher.calls)
  }

  @Test
  fun `a build with no project key reports a sign-out instead of retrying forever`() = runTest {
    // Release builds ship a blank anon key until the OAuth clients exist
    // (148.5c-b), so this branch is reachable in a shipped binary.
    val store = RecordingSessionStore(profileSession(expiresAtEpochSeconds = 100))
    val refresher = ScriptedRefresher { error("must not refresh") }
    val manager = manager(
      store,
      refresher,
      FixedClock(nowEpochSeconds = 90),
      authConfig = AuthConfig(supabaseUrl = "https://project.supabase.co", anonKey = "")
    )

    assertNull(manager.accessToken())
    assertEquals(0, refresher.calls)
    assertEquals(SessionState.SignedOut, manager.state.value)
  }

  @Test
  fun `an adopted session is persisted and published`() = runTest {
    val store = RecordingSessionStore()
    val manager = manager(store, ScriptedRefresher { error("no") }, FixedClock())

    manager.adopt(profileSession(userId = "user-9"))

    assertEquals("user-9", store.peek()?.userId)
    assertEquals(SessionState.SignedIn("user-9"), manager.state.value)
  }

  @Test
  fun `the store is read once and then cached`() = runTest {
    val store = RecordingSessionStore(profileSession(expiresAtEpochSeconds = 10_000))
    val manager = manager(store, ScriptedRefresher { error("no") }, FixedClock())

    repeat(5) { manager.accessToken() }

    assertEquals(1, store.reads)
  }
}
