package com.weekclip.android.upload.api

import retrofit2.http.*
import com.weekclip.android.upload.model.*
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.RequestBody
import retrofit2.Response

/**
 * Retrofit service for upload-related API endpoints.
 */
interface UploadApiService {
  /**
   * Create a media draft (video or photo bundle).
   */
  @POST("medias/draft")
  suspend fun createMediaDraft(
    @Body request: CreateMediaDraftRequest
  ): MediaDraft

  /**
   * Create an upload session for a media.
   */
  @POST("medias/{mediaId}/upload-session")
  suspend fun createUploadSession(
    @Path("mediaId") mediaId: String,
    @Body request: CreateUploadSessionRequest
  ): UploadSession

  /**
   * Request signed URLs for multipart chunks.
   */
  @POST("upload-sessions/{sessionId}/part-urls")
  suspend fun requestPartUrls(
    @Path("sessionId") sessionId: String,
    @Body request: RequestPartUrlsRequest
  ): List<SignedUploadPart>

  /**
   * Complete a multipart upload.
   */
  @POST("upload-sessions/{sessionId}/complete")
  suspend fun completeUpload(
    @Path("sessionId") sessionId: String,
    @Body request: CompleteMultipartRequest
  ): CompleteUploadResponse

  /**
   * Batch presign photos for upload.
   */
  @POST("medias/{mediaId}/presign-photos")
  suspend fun presignPhotos(
    @Path("mediaId") mediaId: String,
    @Body request: BatchPresignPhotosRequest
  ): BatchPresignPhotosResponse

  /**
   * Finalize photo bundle upload.
   */
  @POST("medias/{mediaId}/finalize-photos")
  suspend fun finalizePhotos(
    @Path("mediaId") mediaId: String,
    @Body request: FinalizePhotosRequest
  ): FinalizePhotosResponse

  /**
   * Upload part to S3 presigned URL (raw PUT).
   */
  @PUT("{url}")
  suspend fun uploadPart(
    @Path("url", encoded = true) url: String,
    @Body body: RequestBody
  ): Response<Void>

  /**
   * Upload file to S3 presigned URL (raw PUT).
   */
  @PUT("{url}")
  suspend fun uploadFile(
    @Path("url", encoded = true) url: String,
    @Body body: RequestBody
  ): Response<Void>
}

/**
 * Request to create a media draft.
 */
@kotlinx.serialization.Serializable
data class CreateMediaDraftRequest(
  val title: String,
  @kotlinx.serialization.SerialName("media_type")
  val mediaType: String // "video" | "photo_bundle"
)

/**
 * Request to create an upload session.
 */
@kotlinx.serialization.Serializable
data class CreateUploadSessionRequest(
  @kotlinx.serialization.SerialName("upload_type")
  val uploadType: String // "video" | "photos"
)

/**
 * Request for part URLs.
 */
@kotlinx.serialization.Serializable
data class RequestPartUrlsRequest(
  @kotlinx.serialization.SerialName("part_count")
  val partCount: Int
)

/**
 * API client for upload operations.
 */
@Singleton
class UploadApiClient @Inject constructor(
  private val apiService: UploadApiService
) {

  suspend fun createMediaDraft(
    title: String,
    mediaType: String // "video" or "photo_bundle"
  ): MediaDraft {
    return apiService.createMediaDraft(
      CreateMediaDraftRequest(title, mediaType)
    )
  }

  suspend fun createUploadSession(
    mediaId: String,
    uploadType: String
  ): UploadSession {
    return apiService.createUploadSession(
      mediaId,
      CreateUploadSessionRequest(uploadType)
    )
  }

  suspend fun requestPartUrls(
    sessionId: String,
    partCount: Int
  ): List<SignedUploadPart> {
    return apiService.requestPartUrls(
      sessionId,
      RequestPartUrlsRequest(partCount)
    )
  }

  suspend fun completeUpload(
    sessionId: String,
    partEtags: List<PartEtag>
  ): CompleteUploadResponse {
    return apiService.completeUpload(
      sessionId,
      CompleteMultipartRequest(sessionId, partEtags)
    )
  }

  suspend fun presignPhotos(
    mediaId: String,
    photos: List<PhotoPresignRequest>
  ): BatchPresignPhotosResponse {
    return apiService.presignPhotos(
      mediaId,
      BatchPresignPhotosRequest(mediaId, photos)
    )
  }

  suspend fun finalizePhotos(
    mediaId: String,
    sessionId: String,
    photoIds: List<String>
  ): FinalizePhotosResponse {
    return apiService.finalizePhotos(
      mediaId,
      FinalizePhotosRequest(mediaId, sessionId, photoIds)
    )
  }

  suspend fun uploadPartToS3(
    url: String,
    body: RequestBody
  ): Response<Void> {
    return apiService.uploadPart(url, body)
  }

  suspend fun uploadFileToS3(
    url: String,
    body: RequestBody
  ): Response<Void> {
    return apiService.uploadFile(url, body)
  }
}
