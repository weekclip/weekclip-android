package com.weekclip.android.studio.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Studio media item (video or photo bundle).
 */
@Serializable
data class StudioMedia(
  val id: String,
  val title: String,
  val uploader: String,
  @SerialName("uploaded_at")
  val uploadedAt: String,
  val duration: String,
  @SerialName("duration_seconds")
  val durationSeconds: Int? = null,
  val format: String,
  val size: String,
  val resolution: String,
  val status: String, // "Ready" | "Processing"
  val type: String, // "video" | "photo_bundle"
  val lifecycle: String, // "active" | "expired"
  @SerialName("expires_at")
  val expiresAt: String? = null,
  @SerialName("purge_at")
  val purgeAt: String? = null,
  @SerialName("moderation_status")
  val moderationStatus: String = "clear",
  @SerialName("poster_url")
  val posterUrl: String? = null,
  @SerialName("poster_thumb_url")
  val posterThumbUrl: String? = null,
  val preview: PreviewInfo,
  @SerialName("extra_count")
  val extraCount: Int = 0
)

@Serializable
data class PreviewInfo(
  val status: String, // "Ready" | "Processing" | "Missing"
  @SerialName("manifest_url")
  val manifestUrl: String? = null
)

/**
 * API response wrapper for media list.
 */
@Serializable
data class MediaListResponse(
  val data: MediaListData
)

@Serializable
data class MediaListData(
  val items: List<StudioMedia>,
  @SerialName("next_cursor")
  val nextCursor: String? = null,
  @SerialName("expired_count")
  val expiredCount: Int? = null,
  @SerialName("expired_bytes")
  val expiredBytes: Long? = null
)
