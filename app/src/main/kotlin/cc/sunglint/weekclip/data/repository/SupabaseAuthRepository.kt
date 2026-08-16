package cc.sunglint.weekclip.data.repository

import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.core.session.AuthConfig
import cc.sunglint.weekclip.core.session.ProfileSession
import cc.sunglint.weekclip.core.session.SessionClock
import cc.sunglint.weekclip.data.remote.auth.PkceGrantRequest
import cc.sunglint.weekclip.data.remote.auth.SupabaseAuthService
import cc.sunglint.weekclip.data.remote.auth.SupabaseTokenResponse
import cc.sunglint.weekclip.di.IoDispatcher
import cc.sunglint.weekclip.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuthRepository] over Supabase GoTrue.
 *
 * ### Not routed through `ApiCall`
 *
 * Every other repository folds its failures with `ApiCall.envelope`, and this
 * one deliberately does not: GoTrue is not weekclip's API and does not speak
 * weekclip's `{ data, error }` envelope. Its grant response is a bare object,
 * and its errors are GoTrue's own shape. Pushing it through the shared helper
 * would mean teaching that helper about a second wire format so it could report
 * `MalformedResponse` for every successful sign-in.
 *
 * The transport mapping below is still the same mapping, on purpose —
 * `SupabaseSessionRefresher` next door reaches the same conclusions about the
 * same endpoint.
 *
 * ### 400 is the interesting status
 *
 * GoTrue answers **400** for a bad or already-spent authorization code, not
 * 401. Reporting that as [AppError.Unauthorized] would be a lie in the one
 * place it matters: the user is not unauthorized, the exchange failed, and the
 * fix is to press the button again — which is exactly what the wireframe's
 * error state offers ("다시 시도").
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
  private val service: SupabaseAuthService,
  private val authConfig: AuthConfig,
  private val clock: SessionClock,
  @IoDispatcher private val io: CoroutineDispatcher
) : AuthRepository {

  override suspend fun exchangeAuthCode(
    code: String,
    verifier: String
  ): AppResult<ProfileSession> = withContext(io) {
    if (!authConfig.isConfigured) {
      // Reachable, not hypothetical: a build with no project key compiled in
      // points the auth Retrofit at a placeholder host (`NetworkModule`).
      // Better to say so than to fail DNS resolution and call it "offline".
      return@withContext AppResult.Failure(AppError.Unexpected(IllegalStateException(NOT_CONFIGURED)))
    }

    try {
      val response = service.exchangeAuthCode(
        grantType = GRANT_TYPE,
        body = PkceGrantRequest(authCode = code, codeVerifier = verifier)
      )

      when {
        !response.isSuccessful -> AppResult.Failure(
          AppError.Server(status = response.code())
        )

        else -> response.body()?.let(::toSession)?.let { AppResult.Success(it) }
          ?: AppResult.Failure(AppError.MalformedResponse)
      }
    } catch (e: SocketTimeoutException) {
      // Before IOException — it is a subclass. Same ordering trap ApiCall documents.
      AppResult.Failure(AppError.Timeout)
    } catch (e: UnknownHostException) {
      AppResult.Failure(AppError.Offline)
    } catch (e: IOException) {
      AppResult.Failure(AppError.Offline)
    } catch (e: SerializationException) {
      // A 200 whose body is not a grant. Retrofit's converter throws here
      // instead of handing back a null body; without this branch it escapes
      // into the ViewModel's coroutine as an uncaught crash.
      AppResult.Failure(AppError.MalformedResponse)
    }
  }

  /**
   * Unlike a refresh, an exchange has no previous session to fall back on, so
   * `user.id` is genuinely required here. A grant with no subject is not a
   * session this app can act on — `ProfileSession.userId` is what the dashboard
   * and the logs identify.
   */
  private fun toSession(body: SupabaseTokenResponse): ProfileSession? {
    if (body.accessToken.isBlank() || body.refreshToken.isBlank()) return null

    val userId = body.user?.id?.takeIf { it.isNotBlank() } ?: return null
    val expiresAt = body.expiresAt
      ?: body.expiresIn?.let { clock.nowEpochSeconds() + it }
      ?: return null

    return ProfileSession(
      accessToken = body.accessToken,
      refreshToken = body.refreshToken,
      expiresAtEpochSeconds = expiresAt,
      userId = userId
    )
  }

  private companion object {
    const val GRANT_TYPE = "pkce"
    const val NOT_CONFIGURED = "no Supabase project key in this build"
  }
}
