package cc.sunglint.weekclip.debug

import android.util.Log
import cc.sunglint.weekclip.BuildConfig
import cc.sunglint.weekclip.core.session.AuthConfig
import cc.sunglint.weekclip.core.session.ProfileSession
import cc.sunglint.weekclip.core.session.SessionClock
import cc.sunglint.weekclip.core.session.SessionManager
import cc.sunglint.weekclip.core.session.SessionState
import cc.sunglint.weekclip.di.AppInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Puts a real session in front of the app on a debug build, so that "the
 * session store works" can be checked by looking at the screen instead of only
 * at a test report.
 *
 * ### It signs in **only if there is nothing stored**
 *
 * That order is the interesting part, not an optimisation. On first launch the
 * log says `signed in`; on every launch after that it says `restored`, and the
 * dashboard fills without a single call to Supabase. A build that signed in
 * every time would render exactly the same screen whether or not persistence
 * worked at all — which is the failure mode 148.3d warned about: a check that
 * cannot fail is not a check.
 *
 * Silent and inert unless `local.properties` supplies credentials, so a clean
 * checkout behaves like a release build: no session, `AppError.Unauthorized`,
 * error screen.
 */
class DebugAutoSignIn @Inject constructor(
  private val sessionManager: SessionManager,
  private val service: DebugSupabaseAuthService,
  private val authConfig: AuthConfig,
  private val clock: SessionClock
) : AppInitializer {

  override fun initialize(scope: CoroutineScope) {
    val email = BuildConfig.DEBUG_SIGN_IN_EMAIL
    val password = BuildConfig.DEBUG_SIGN_IN_PASSWORD

    if (email.isBlank() || password.isBlank()) {
      Log.i(TAG, "no debug credentials in local.properties — staying signed out")
      return
    }
    if (!authConfig.isConfigured) {
      Log.w(TAG, "weekclip.supabaseAnonKey.dev is missing — cannot sign in")
      return
    }

    scope.launch {
      when (val restored = sessionManager.reload()) {
        is SessionState.SignedIn -> {
          Log.i(TAG, "restored a stored session for ${restored.userId} — no network needed")
          return@launch
        }
        else -> Log.i(TAG, "nothing stored; signing in as $email")
      }

      val response = runCatching {
        service.signInWithPassword(
          grantType = GRANT_TYPE,
          body = PasswordGrantRequest(email = email, password = password)
        )
      }.getOrElse {
        Log.w(TAG, "sign-in call failed: ${it.javaClass.simpleName}")
        return@launch
      }

      val body = response.body()
      if (!response.isSuccessful || body == null) {
        Log.w(TAG, "sign-in rejected with HTTP ${response.code()}")
        return@launch
      }

      val userId = body.user?.id
      val expiresAt = body.expiresAt ?: body.expiresIn?.let { clock.nowEpochSeconds() + it }
      if (userId == null || expiresAt == null) {
        Log.w(TAG, "grant response was missing user or expiry")
        return@launch
      }

      sessionManager.adopt(
        ProfileSession(
          accessToken = body.accessToken,
          refreshToken = body.refreshToken,
          expiresAtEpochSeconds = expiresAt,
          userId = userId
        )
      )
      Log.i(TAG, "signed in as $userId; session stored")
    }
  }

  private companion object {
    const val TAG = "WeekclipDebugAuth"
    const val GRANT_TYPE = "password"
  }
}
