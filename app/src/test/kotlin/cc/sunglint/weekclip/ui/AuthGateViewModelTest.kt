package cc.sunglint.weekclip.ui

import cc.sunglint.weekclip.core.auth.SignInFlowStore
import cc.sunglint.weekclip.core.session.AuthConfig
import cc.sunglint.weekclip.core.session.FixedClock
import cc.sunglint.weekclip.core.session.InMemoryPreferencesDataStore
import cc.sunglint.weekclip.core.session.RecordingSessionStore
import cc.sunglint.weekclip.core.session.RefreshOutcome
import cc.sunglint.weekclip.core.session.ScriptedRefresher
import cc.sunglint.weekclip.core.session.SessionManager
import cc.sunglint.weekclip.core.session.profileSession
import cc.sunglint.weekclip.ui.auth.AuthGateUiState
import cc.sunglint.weekclip.ui.auth.AuthGateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The first gate's rules, one test each.
 *
 * This is where PRD-0008 D4 actually lives. Everything else about sign-in —
 * PKCE, the browser, the exchange — is machinery that can be right while the
 * product rule is wrong, and the product rule is the thing a reader of this
 * repo in six months will want stated somewhere they can run.
 */
class AuthGateViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  private val dataStore = InMemoryPreferencesDataStore()
  private val flowStore = SignInFlowStore(dataStore)

  /**
   * `viewModelScope` is hardwired to `Dispatchers.Main`, which does not exist
   * in a JVM unit test.
   */
  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun sessionManager(stored: cc.sunglint.weekclip.core.session.ProfileSession? = null) =
    SessionManager(
      store = RecordingSessionStore(stored),
      refresher = ScriptedRefresher { RefreshOutcome.Unavailable },
      authConfig = AuthConfig(supabaseUrl = "https://project.supabase.co", anonKey = "anon"),
      clock = FixedClock(nowEpochSeconds = 0)
    )

  @Test
  fun `renders nothing until the stored session has been read`() = runTest {
    val viewModel = AuthGateViewModel(sessionManager(stored = profileSession()), flowStore)

    // Before the store read completes. Reporting SignedOut here is the bug this
    // state exists to prevent: it flashes a login screen at a signed-in user.
    assertEquals(AuthGateUiState.Unknown, viewModel.uiState.value)

    runCurrent()
    assertEquals(AuthGateUiState.SignedIn, viewModel.uiState.value)
  }

  @Test
  fun `no stored session means the first screen is the gate`() = runTest {
    val viewModel = AuthGateViewModel(sessionManager(stored = null), flowStore)
    runCurrent()

    assertEquals(AuthGateUiState.SignedOut, viewModel.uiState.value)
  }

  @Test
  fun `a share link opens with no account at all`() = runTest {
    val viewModel = AuthGateViewModel(sessionManager(stored = null), flowStore)
    runCurrent()

    viewModel.onIncomingLink("share/abc123")
    runCurrent()

    // The single exception in D4, and it is the API's shape rather than a
    // preference — see WeekclipRoutes.isGuestRoute.
    assertEquals(AuthGateUiState.Guest("share/abc123"), viewModel.uiState.value)
    // Nothing was parked: a guest is not a user on their way to signing in.
    assertNull(flowStore.takeIntendedRoute())
  }

  @Test
  fun `an invite link goes through the gate and is remembered`() = runTest {
    val viewModel = AuthGateViewModel(sessionManager(stored = null), flowStore)
    runCurrent()

    viewModel.onIncomingLink("invite/tok")
    runCurrent()

    // Accepting an invite attaches a studio to an account, so there has to be
    // an account. weekclip-web reaches the same conclusion in flow F3.
    assertEquals(AuthGateUiState.SignedOut, viewModel.uiState.value)
    assertEquals("invite/tok", flowStore.takeIntendedRoute())
  }

  @Test
  fun `signing in goes to the link that was interrupted, not the dashboard`() = runTest {
    val manager = sessionManager(stored = null)
    val viewModel = AuthGateViewModel(manager, flowStore)
    runCurrent()

    viewModel.onIncomingLink("studios/s1/media/m1")
    runCurrent()
    assertEquals(AuthGateUiState.SignedOut, viewModel.uiState.value)

    manager.adopt(profileSession())
    runCurrent()

    assertEquals(AuthGateUiState.SignedIn, viewModel.uiState.value)
    assertEquals("studios/s1/media/m1", viewModel.pendingNavigation.value)
  }

  @Test
  fun `an intended route fires once`() = runTest {
    val manager = sessionManager(stored = null)
    val viewModel = AuthGateViewModel(manager, flowStore)
    runCurrent()
    viewModel.onIncomingLink("invite/tok")
    runCurrent()
    manager.adopt(profileSession())
    runCurrent()

    viewModel.onNavigated()

    // Re-navigating on the next recomposition would yank the user out of
    // whatever they opened next — the reason this is an instruction and not a
    // state.
    assertNull(viewModel.pendingNavigation.value)
  }

  @Test
  fun `a link that arrives while signed in navigates instead of parking`() = runTest {
    val viewModel = AuthGateViewModel(sessionManager(stored = profileSession()), flowStore)
    runCurrent()

    viewModel.onIncomingLink("studios/s1")
    runCurrent()

    assertEquals(AuthGateUiState.SignedIn, viewModel.uiState.value)
    assertEquals("studios/s1", viewModel.pendingNavigation.value)
  }

  @Test
  fun `a share link while signed in opens inside the app, not as a guest`() = runTest {
    val viewModel = AuthGateViewModel(sessionManager(stored = profileSession()), flowStore)
    runCurrent()

    viewModel.onIncomingLink("share/abc123")
    runCurrent()

    // The guest surface is for people with no account. Someone who has one gets
    // the same link inside the app, where SessionAxis picks the share
    // credential per request rather than the gate picking it for the session.
    assertEquals(AuthGateUiState.SignedIn, viewModel.uiState.value)
    assertEquals("share/abc123", viewModel.pendingNavigation.value)
  }

  @Test
  fun `backing out of a shared album lands on the gate`() = runTest {
    val viewModel = AuthGateViewModel(sessionManager(stored = null), flowStore)
    runCurrent()
    viewModel.onIncomingLink("share/abc123")
    runCurrent()

    viewModel.onLeaveGuest()
    runCurrent()

    // Not "close the app": someone who opened a shared album may well have an
    // account, and this is the only way for them to reach it.
    assertEquals(AuthGateUiState.SignedOut, viewModel.uiState.value)
  }

  @Test
  fun `a link that arrives before the store has been read is not decided early`() = runTest {
    val viewModel = AuthGateViewModel(sessionManager(stored = profileSession()), flowStore)

    // No runCurrent() first — the session is still Unknown, which is exactly
    // the cold-start-from-a-link case. Deciding here would park the route and
    // show the gate to someone who is signed in.
    viewModel.onIncomingLink("studios/s1")
    runCurrent()

    assertEquals(AuthGateUiState.SignedIn, viewModel.uiState.value)
    assertEquals("studios/s1", viewModel.pendingNavigation.value)
    assertNull(flowStore.takeIntendedRoute())
  }
}
