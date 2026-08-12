package com.weekclip.android.studio.api

import retrofit2.http.GET
import retrofit2.http.Query
import com.weekclip.android.studio.model.StudiosResponse
import com.weekclip.android.studio.model.MediaListResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit interface for studio API endpoints.
 */
interface StudioApiService {
  @GET("studios")
  suspend fun getStudios(): StudiosResponse

  @GET("studios/{studioId}/media")
  suspend fun getMediaList(
    @retrofit2.http.Path("studioId") studioId: String,
    @Query("cursor") cursor: String? = null,
    @Query("limit") limit: Int = 20,
    @Query("lifecycle") lifecycle: String = "active"
  ): MediaListResponse
}

/**
 * Repository for studio operations.
 */
@Singleton
class StudioApiClient @Inject constructor(
  private val apiService: StudioApiService
) {

  suspend fun fetchStudios() = apiService.getStudios()

  suspend fun fetchMediaList(
    studioId: String,
    cursor: String? = null,
    limit: Int = 20
  ) = apiService.getMediaList(studioId, cursor, limit)
}
