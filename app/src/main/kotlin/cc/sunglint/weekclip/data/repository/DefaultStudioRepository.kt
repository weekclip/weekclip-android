package cc.sunglint.weekclip.data.repository

import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.data.remote.ApiCall
import cc.sunglint.weekclip.data.remote.WeekclipApiService
import cc.sunglint.weekclip.data.remote.dto.toDomain
import cc.sunglint.weekclip.di.IoDispatcher
import cc.sunglint.weekclip.domain.model.Studio
import cc.sunglint.weekclip.domain.repository.StudioRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote-only for now.
 *
 * The data-layer skill prescribes an offline-first repository with Room as the
 * source of truth. That is deliberately not here: PRD-0008 lists no offline
 * requirement, and a Room schema with no read path is a migration liability
 * from the day it lands. The seam that makes it addable later is the interface
 * — callers already cannot tell where the data came from.
 *
 * `withContext(io)` even though Retrofit's `suspend` calls are already
 * main-safe: it is the DTO mapping over a large list that is not, and the
 * architecture skill's rule ("repositories are main-safe") should hold for the
 * whole method rather than for the part that happens to be network.
 */
@Singleton
class DefaultStudioRepository @Inject constructor(
  private val service: WeekclipApiService,
  private val json: Json,
  @IoDispatcher private val io: CoroutineDispatcher
) : StudioRepository {

  override suspend fun getStudios(): AppResult<List<Studio>> = withContext(io) {
    ApiCall.envelope(
      json = json,
      block = { service.getStudios() },
      transform = { payload -> payload.items.map { it.toDomain() } }
    )
  }
}
