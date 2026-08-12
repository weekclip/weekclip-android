package com.weekclip.android.upload.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API response for creating a media draft.
 */
@Serializable
data class MediaDraft(
  val id: String,
  val title: String,
  @SerialName("studio_id")
  val studioId: String,
  val status: String // "draft"
)

/**
 * Upload session info from API.
 */
@Serializable
data class UploadSession(
  val id: String,
  @SerialName("media_id")
  val mediaId: String,
  @SerialName("upload_type")
  val uploadType: String, // "video" | "photos"
  @SerialName("part_size")
  val partSize: Long, // 10485760 for 10MB
  @SerialName("created_at")
  val createdAt: String,
  @SerialName("expires_at")
  val expiresAt: String
)

/**
 * Signed URL for a multipart upload chunk.
 */
@Serializable
data class SignedUploadPart(
  val id: String,
  @SerialName("part_number")
  val partNumber: Int,
  @SerialName("upload_url")
  val uploadUrl: String,
  val size: Long // exact size of this part
)

/**
 * Complete multipart upload request.
 */
@Serializable
data class CompleteMultipartRequest(
  @SerialName("session_id")
  val sessionId: String,
  @SerialName("part_etags")
  val partEtags: List<PartEtag>
)

@Serializable
data class PartEtag(
  @SerialName("part_number")
  val partNumber: Int,
  val etag: String
)

/**
 * Response after completing upload.
 */
@Serializable
data class CompleteUploadResponse(
  val status: String, // "success" | "processing"
  @SerialName("media_id")
  val mediaId: String,
  @SerialName("upload_session_id")
  val uploadSessionId: String
)

/**
 * Photo info for presigning.
 */
@Serializable
data class PhotoPresignRequest(
  val name: String,
  @SerialName("mime_type")
  val mimeType: String,
  val size: Long,
  @SerialName("gps_lat")
  val gpsLat: Double? = null,
  @SerialName("gps_lon")
  val gpsLon: Double? = null
)

/**
 * Presigned URL for a single photo (output).
 */
@Serializable
data class PhotoPresignedEntry(
  val id: String,
  val name: String,
  @SerialName("upload_url")
  val uploadUrl: String,
  @SerialName("thumbnail_upload_url")
  val thumbnailUploadUrl: String? = null, // for WebP thumbnail
  val size: Long,
  @SerialName("created_at")
  val createdAt: String
)

/**
 * Batch presign request for photos.
 */
@Serializable
data class BatchPresignPhotosRequest(
  @SerialName("media_id")
  val mediaId: String,
  val photos: List<PhotoPresignRequest>
)

/**
 * Response with presigned URLs.
 */
@Serializable
data class BatchPresignPhotosResponse(
  @SerialName("media_id")
  val mediaId: String,
  val photos: List<PhotoPresignedEntry>,
  @SerialName("session_id")
  val sessionId: String
)

/**
 * Photo bundle upload finalize request.
 */
@Serializable
data class FinalizePhotosRequest(
  @SerialName("media_id")
  val mediaId: String,
  @SerialName("session_id")
  val sessionId: String,
  @SerialName("photo_ids")
  val photoIds: List<String> // IDs of successfully uploaded photos
)

/**
 * Response after finalizing photo bundle.
 */
@Serializable
data class FinalizePhotosResponse(
  val status: String, // "success" | "processing"
  @SerialName("media_id")
  val mediaId: String,
  @SerialName("photo_count")
  val photoCount: Int
)

/**
 * Structured upload error information.
 */
data class UploadError(
  val code: String, // "INSUFFICIENT_CREDIT", "NETWORK_ERROR", "TIMEOUT", etc.
  val message: String,
  val retryable: Boolean = true,
  val details: Map<String, String> = emptyMap() // e.g., required, available, shortfall for 402
)

/**
 * Progress tracking for uploads.
 */
data class UploadProgress(
  val totalBytes: Long,
  val uploadedBytes: Long,
  val completedParts: Int = 0,
  val totalParts: Int = 0,
  val speedBytesPerSecond: Long = 0L, // bytes/sec for real-time speed
  val phase: UploadPhase = UploadPhase.VALIDATING
)

enum class UploadPhase {
  VALIDATING,
  CREATING_DRAFT,
  CREATING_SESSION,
  REQUESTING_URLS,
  UPLOADING,
  FINALIZING,
  COMPLETE
}

/**
 * Status of individual upload queue item.
 */
enum class UploadStatus {
  QUEUED,
  UPLOADING,
  DONE,
  ERROR,
  CANCELED
}

/**
 * Item in the upload queue.
 */
data class UploadQueueItem(
  val id: String, // unique queue ID
  val title: String,
  val fileName: String,
  val totalBytes: Long,
  val status: UploadStatus = UploadStatus.QUEUED,
  val progress: Float = 0f, // 0-100
  val errorMessage: String? = null,
  val errorCode: String? = null,
  val uploadType: String, // "video" or "photo_bundle"
  val photoCount: Int = 0, // for photo bundles
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * Input for enqueueing an upload.
 */
sealed class EnqueueUploadInput {
  data class Video(
    val title: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long
  ) : EnqueueUploadInput()

  data class PhotoBundle(
    val title: String,
    val photoPaths: List<String>, // file paths to JPEG/PNG/WebP
    val photoCount: Int = photoPaths.size
  ) : EnqueueUploadInput()
}
