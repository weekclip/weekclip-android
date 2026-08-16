package cc.sunglint.weekclip.debug

import android.util.Log
import cc.sunglint.weekclip.BuildConfig
import cc.sunglint.weekclip.core.session.AuthConfig
import cc.sunglint.weekclip.core.session.ProfileSession
import cc.sunglint.weekclip.core.session.SessionClock
import cc.sunglint.weekclip.core.session.SessionManager
import cc.sunglint.weekclip.ui.auth.DebugSignInAction
import javax.inject.Inject

/**
 * A password grant behind a button on the login screen — **debug builds only**.
 *
 * weekclip's product login is Google and nothing else (weekclip-web
 * `LoginPage.tsx`). This is not a second product login sneaking in; it is the
 * way to put a genuine Supabase token in front of the session code on a device
 * without a Google round trip, which matters for two reasons that outlive
 * 148.5c-b:
 *
 * - the OAuth redirect needs `cc.sunglint.weekclip://auth-callback` in the
 *   Supabase project's allow list, which is console work this repo cannot do;
 * - even once it is there, a flow that leaves the app and comes back is a poor
 *   thing to hang every device check on.
 *
 * The whole file is in `src/debug`, so a release APK contains neither the
 * endpoint, nor the request type, nor the word `password` in a grant.
 *
 * ### It used to run itself, and no longer does
 *
 * Until this task it was an `AppInitializer` that signed in at process start,
 * with a documented "only if nothing is stored" check to prove persistence
 * worked. Both properties survive the move and one improves:
 *
 * | | before | now |
 * |---|---|---|
 * | when | process start, always | only when the login screen is showing |
 * | "nothing stored" check | an `if` inside this class | structural — the login screen is unreachable with a session |
 * | the gate | walked past | exercised on every debug launch |
 *
 * A development build that never sees the gate is a gate nobody is testing.
 */
class DebugPasswordSignIn @Inject constructor(
  private val sessionManager: SessionManager,
  private val service: DebugSupabaseAuthService,
  private val authConfig: AuthConfig,
  private val clock: SessionClock
) : DebugSignInAction {

  override val label: String = "Sign in with a password (debug)"

  override suspend fun signIn(): Boolean {
    // Non-blank by construction: DebugSessionModule does not contribute this
    // action at all when local.properties has no credentials, so the button
    // that reaches here does not exist in that build.
    val email = BuildConfig.DEBUG_SIGN_IN_EMAIL
    val password = BuildConfig.DEBUG_SIGN_IN_PASSWORD

    if (!authConfig.isConfigured) {
      Log.w(TAG, "weekclip.supabaseAnonKey.dev is missing — cannot sign in")
      return false
    }

    Log.i(TAG, "signing in as $email")

    val response = runCatching {
      service.signInWithPassword(
        grantType = GRANT_TYPE,
        body = PasswordGrantRequest(email = email, password = password)
      )
    }.getOrElse {
      Log.w(TAG, "sign-in call failed: ${it.javaClass.simpleName}")
      return false
    }

    val body = response.body()
    if (!response.isSuccessful || body == null) {
      Log.w(TAG, "sign-in rejected with HTTP ${response.code()}")
      return false
    }

    val userId = body.user?.id
    val expiresAt = body.expiresAt ?: body.expiresIn?.let { clock.nowEpochSeconds() + it }
    if (userId == null || expiresAt == null) {
      Log.w(TAG, "grant response was missing user or expiry")
      return false
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
    return true
  }

  private companion object {
    const val TAG = "WeekclipDebugAuth"
    const val GRANT_TYPE = "password"
  }
}
