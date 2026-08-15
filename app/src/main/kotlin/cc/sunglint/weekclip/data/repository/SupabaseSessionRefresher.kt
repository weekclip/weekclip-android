package cc.sunglint.weekclip.data.repository

import cc.sunglint.weekclip.core.session.ProfileSession
import cc.sunglint.weekclip.core.session.RefreshOutcome
import cc.sunglint.weekclip.core.session.SessionClock
import cc.sunglint.weekclip.core.session.SessionRefresher
import cc.sunglint.weekclip.data.remote.auth.RefreshTokenRequest
import cc.sunglint.weekclip.data.remote.auth.SupabaseAuthService
import cc.sunglint.weekclip.data.remote.auth.SupabaseTokenResponse
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject

/**
 * [SessionRefresher] over Supabase GoTrue.
 *
 * The status mapping is the whole file, and it is deliberate:
 *
 * | status | outcome | why |
 * |--------|---------|-----|
 * | 2xx with a token | [RefreshOutcome.Refreshed] | |
 * | 400 · 401 · 403 | [RefreshOutcome.Rejected] | GoTrue answers **400** for `refresh_token_not_found`, not 401. Treating only 401 as fatal would leave a dead session retrying forever |
 * | 2xx with no token | [RefreshOutcome.Unavailable] | the contract broke; do not sign the user out over our own parsing |
 * | anything else, `IOException` | [RefreshOutcome.Unavailable] | a lift, a captive portal, a 502 |
 */
class SupabaseSessionRefresher @Inject constructor(
  private val service: SupabaseAuthService,
  private val clock: SessionClock
) : SessionRefresher {

  override suspend fun refresh(session: ProfileSession): RefreshOutcome = try {
    val response = service.refresh(
      grantType = GRANT_TYPE,
      body = RefreshTokenRequest(refreshToken = session.refreshToken)
    )

    when {
      response.isSuccessful -> response.body()
        ?.let { toSession(it, previous = session) }
        ?.let(RefreshOutcome::Refreshed)
        ?: RefreshOutcome.Unavailable

      response.code() in REJECTING_STATUSES -> RefreshOutcome.Rejected

      else -> RefreshOutcome.Unavailable
    }
  } catch (e: IOException) {
    RefreshOutcome.Unavailable
  } catch (e: SerializationException) {
    // A 200 whose body is not a grant. Retrofit's converter throws here rather
    // than handing back a null body, and this is NOT an IOException — without
    // this branch the exception escapes `refresh`, unwinds through the
    // `runBlocking` in SessionCredentialProvider, and surfaces from inside an
    // OkHttp interceptor, where it becomes a failed call with no AppError and
    // no session decision at all. Found by the test below, not by reasoning.
    RefreshOutcome.Unavailable
  }

  private fun toSession(body: SupabaseTokenResponse, previous: ProfileSession): ProfileSession? {
    if (body.accessToken.isBlank() || body.refreshToken.isBlank()) {
      return null
    }

    val expiresAt = body.expiresAt
      ?: body.expiresIn?.let { clock.nowEpochSeconds() + it }
      ?: return null

    return ProfileSession(
      accessToken = body.accessToken,
      refreshToken = body.refreshToken,
      expiresAtEpochSeconds = expiresAt,
      // A refresh response is not required to re-state the user. Keeping the
      // previous id is right *because* a refresh cannot change identity — the
      // refresh token belongs to one user by construction.
      userId = body.user?.id ?: previous.userId
    )
  }

  private companion object {
    const val GRANT_TYPE = "refresh_token"
    val REJECTING_STATUSES = setOf(400, 401, 403)
  }
}
