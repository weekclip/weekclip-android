package cc.sunglint.weekclip.data.repository

import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.data.remote.ApiCall
import cc.sunglint.weekclip.data.remote.WeekclipApiService
import cc.sunglint.weekclip.data.remote.dto.toDomain
import cc.sunglint.weekclip.di.IoDispatcher
import cc.sunglint.weekclip.domain.model.AppReleasePolicy
import cc.sunglint.weekclip.domain.repository.AppReleaseRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads `GET /app/version` and keeps only this platform's half.
 *
 * The endpoint is unauthenticated by design (weekclip-api): an app too old to
 * be allowed in may also be too old to sign in, so the check must work before
 * there is a session. The interceptor still attaches a bearer if one happens to
 * exist — harmless, and not worth a second HTTP client to avoid.
 */
@Singleton
class DefaultAppReleaseRepository @Inject constructor(
  private val service: WeekclipApiService,
  private val json: Json,
  @IoDispatcher private val io: CoroutineDispatcher
) : AppReleaseRepository {

  override suspend fun policy(): AppResult<AppReleasePolicy> = withContext(io) {
    ApiCall.envelope(
      json = json,
      block = { service.getAppVersion() },
      transform = { payload -> payload.android.toDomain() }
    )
  }
}
