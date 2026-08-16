package cc.sunglint.weekclip.ui

import app.cash.turbine.test
import cc.sunglint.weekclip.core.auth.AuthRedirectBus
import cc.sunglint.weekclip.core.auth.OAuthCallback
import cc.sunglint.weekclip.core.auth.SignInFlowStore
import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.core.session.AuthConfig
import cc.sunglint.weekclip.core.session.FixedClock
import cc.sunglint.weekclip.core.session.InMemoryPreferencesDataStore
import cc.sunglint.weekclip.core.session.ProfileSession
import cc.sunglint.weekclip.core.session.RecordingSessionStore
import cc.sunglint.weekclip.core.session.RefreshOutcome
import cc.sunglint.weekclip.core.session.ScriptedRefresher
import cc.sunglint.weekclip.core.session.SessionManager
import cc.sunglint.weekclip.core.session.SessionState
import cc.sunglint.weekclip.core.session.profileSession
import cc.sunglint.weekclip.domain.repository.AuthRepository
import cc.sunglint.weekclip.domain.usecase.BeginGoogleSignInUseCase
import cc.sunglint.weekclip.domain.usecase.CompleteGoogleSignInUseCase
import cc.sunglint.weekclip.ui.auth.LoginEffect
import cc.sunglint.weekclip.ui.auth.LoginError
import cc.sunglint.weekclip.ui.auth.LoginPhase
import cc.sunglint.weekclip.ui.auth.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The login screen's state machine, with the browser replaced by a bus.
 *
 * The test worth reading is `a cancelled sign-in returns to the button`. Android
 * reports nothing when a user backs out of a Custom Tab, so the app infers it
 * from an empty bus at resume — and if that inference is wrong the spinner
 * never goes away and the only way out of the app is to kill it. That is not a
 * bug a screenshot catches.
 */
class LoginViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  private val dataStore = InMemoryPreferencesDataStore()
  private val flowStore = SignInFlowStore(dataStore)
  private val redirectBus = AuthRedirectBus()
  private val sessionStore = RecordingSessionStore()

  private val config = AuthConfig(supabaseUrl = "https://project.supabase.co", anonKey = "anon")

  private val sessionManager = SessionManager(
    store = sessionStore,
    refresher = ScriptedRefresher { RefreshOutcome.Unavailable },
    authConfig = config,
    clock = FixedClock(nowEpochSeconds = 0)
  )

  private var exchange: (String, String) -> AppResult<ProfileSession> =
    { _, _ -> AppResult.Success(profileSession()) }

  private val repository = object : AuthRepository {
    var lastVerifier: String? = null
      private set

    override suspend fun exchangeAuthCode(
      code: String,
      verifier: String
    ): AppResult<ProfileSession> {
      lastVerifier = verifier
      return exchange(code, verifier)
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(authConfig: AuthConfig = config) = LoginViewModel(
    beginGoogleSignIn = BeginGoogleSignInUseCase(authConfig, flowStore),
    completeGoogleSignIn = CompleteGoogleSignInUseCase(flowStore, repository, sessionManager),
    redirectBus = redirectBus,
    flowStore = flowStore,
    debugSignIns = emptySet()
  )

  @Test
  fun `pressing the button hands an authorize url to the screen`() = runTest {
    val viewModel = viewModel()

    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()

      val effect = awaitItem() as LoginEffect.OpenAuthorizeUrl
      assertTrue(effect.url, effect.url.startsWith("https://project.supabase.co/auth/v1/authorize?"))
    }
    assertEquals(LoginPhase.Connecting, viewModel.uiState.value.phase)
  }

  @Test
  fun `the verifier is on disk before the browser opens`() = runTest {
    val viewModel = viewModel()

    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()
      awaitItem()
    }

    // Reversed, a fast redirect — or a process death — brings the app back
    // holding a code it has no verifier for, which reads to the user as
    // "Google worked and weekclip lost it".
    assertNotNull(flowStore.takeVerifier())
  }

  @Test
  fun `a redirect with a code becomes a session`() = runTest {
    val viewModel = viewModel()
    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()
      awaitItem()
    }

    redirectBus.offer(OAuthCallback.Granted("auth-code-1"))
    viewModel.onReturnedToForeground()
    runCurrent()

    assertEquals(SessionState.SignedIn("user-1"), sessionManager.state.value)
    assertEquals(1, sessionStore.writes)
  }

  @Test
  fun `the exchange presents the verifier this process generated`() = runTest {
    val viewModel = viewModel()
    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()
      awaitItem()
    }
    // Grab it before the exchange consumes it, then put it back.
    val expected = flowStore.takeVerifier()!!
    flowStore.putVerifier(expected)

    redirectBus.offer(OAuthCallback.Granted("auth-code-1"))
    viewModel.onReturnedToForeground()
    runCurrent()

    // Without this the flow is PKCE-shaped but not PKCE: an intercepted code
    // would be redeemable by whoever intercepted it.
    assertEquals(expected, repository.lastVerifier)
  }

  @Test
  fun `a code with no stored verifier is refused without a network call`() = runTest {
    val viewModel = viewModel()

    // No onGoogleSignInClick — this is the shape of a redirect that belongs to
    // some other app's sign-in, or to one this app has already completed.
    redirectBus.offer(OAuthCallback.Granted("auth-code-1"))
    viewModel.onReturnedToForeground()
    runCurrent()

    // Not `state == SignedOut`: with nothing having read the store, the manager
    // is still `Unknown`, and that is correct — "not loaded" is not "signed
    // out". What is being asserted is that no session was created and no
    // request went out.
    assertEquals(0, sessionStore.writes)
    assertNull(repository.lastVerifier)
  }

  @Test
  fun `a cancelled sign-in returns to the button`() = runTest {
    val viewModel = viewModel()
    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()
      awaitItem()
    }

    // The user backed out of the browser: the app resumes and the bus is empty.
    // Android sends no event for this — the ordering documented on
    // AuthRedirectBus (onNewIntent before onResume) is what makes an empty bus
    // here mean "nothing came back" rather than "not yet".
    viewModel.onReturnedToForeground()
    runCurrent()

    assertEquals(LoginPhase.Idle, viewModel.uiState.value.phase)
    assertNull(viewModel.uiState.value.error)
    // The abandoned verifier is cleared, so the next sign-in cannot be matched
    // against it.
    assertNull(flowStore.takeVerifier())
  }

  @Test
  fun `a resume with nothing in flight is not mistaken for a cancellation`() = runTest {
    val viewModel = viewModel()

    // Every resume calls this, including the screen's first.
    viewModel.onReturnedToForeground()
    runCurrent()

    assertEquals(LoginPhase.Idle, viewModel.uiState.value.phase)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun `a refusal is shown as a refusal, not as a failure`() = runTest {
    val viewModel = viewModel()
    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()
      awaitItem()
    }

    redirectBus.offer(OAuthCallback.Denied("access_denied", null))
    viewModel.onReturnedToForeground()
    runCurrent()

    // The wireframe draws these separately because the user's next move
    // differs: retry helps one and not the other.
    assertEquals(LoginError.Denied("access_denied"), viewModel.uiState.value.error)
    assertEquals(LoginPhase.Idle, viewModel.uiState.value.phase)
  }

  @Test
  fun `a failed exchange offers a retry rather than a dead spinner`() = runTest {
    exchange = { _, _ -> AppResult.Failure(AppError.Offline) }
    val viewModel = viewModel()
    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()
      awaitItem()
    }

    redirectBus.offer(OAuthCallback.Granted("auth-code-1"))
    viewModel.onReturnedToForeground()
    runCurrent()

    assertEquals(LoginError.Failed(AppError.Offline), viewModel.uiState.value.error)
    assertEquals(LoginPhase.Idle, viewModel.uiState.value.phase)
    // A failed exchange must not leave a half-session behind.
    assertEquals(0, sessionStore.writes)
  }

  @Test
  fun `a build with no project key says so instead of opening a browser`() = runTest {
    val viewModel = viewModel(authConfig = AuthConfig(supabaseUrl = "", anonKey = ""))

    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()
      expectNoEvents()
    }

    assertEquals(LoginError.NotConfigured, viewModel.uiState.value.error)
    assertEquals(LoginPhase.Idle, viewModel.uiState.value.phase)
  }

  @Test
  fun `a second tap while the browser is open does not start a second sign-in`() = runTest {
    val viewModel = viewModel()

    viewModel.effects.test {
      viewModel.onGoogleSignInClick()
      runCurrent()
      awaitItem()

      // Two Custom Tabs, two verifiers, and the second overwrites the first —
      // so the code from whichever tab the user finishes cannot be exchanged.
      viewModel.onGoogleSignInClick()
      runCurrent()
      expectNoEvents()
    }
  }

  @Test
  fun `the screen says a link is waiting when one is`() = runTest {
    flowStore.putIntendedRoute("invite/tok")

    val viewModel = viewModel()
    runCurrent()

    assertTrue(viewModel.uiState.value.hasIntendedDestination)
  }
}
