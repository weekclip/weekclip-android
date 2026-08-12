package com.weekclip.android.studio.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Studio data model representing a user's studio.
 */
@Serializable
data class Studio(
  val id: String,
  val name: String,
  val role: String, // "Owner" | "Editor" | "Viewer"
  @SerialName("media_count")
  val mediaCount: Int,
  @SerialName("updated_at_label")
  val updatedAtLabel: String,
  @SerialName("created_at_label")
  val createdAtLabel: String,
  @SerialName("cover_class_name")
  val coverClassName: String = ""
)

/**
 * API response wrapper for studios list.
 */
@Serializable
data class StudiosResponse(
  val data: StudiosData
)

@Serializable
data class StudiosData(
  val items: List<Studio>
)
