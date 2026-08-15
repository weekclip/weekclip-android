package cc.sunglint.weekclip.data.remote.dto

import cc.sunglint.weekclip.domain.model.Studio
import cc.sunglint.weekclip.domain.model.StudioRole
import kotlinx.serialization.Serializable

/**
 * `GET /studios` item, exactly as weekclip-api's `presentStudioMembership`
 * emits it. Field names match the wire; do not rename them to read better.
 *
 * Every field but `id` is nullable-with-a-default. That is not defensive
 * padding — the API has already added fields to this shape once (`slug`), and a
 * strict decoder turns a server-side addition into a client-side crash for
 * everyone who has not updated.
 */
@Serializable
data class StudioDto(
  val id: String,
  val slug: String? = null,
  val name: String? = null,
  val ownerId: String? = null,
  val createdAt: String? = null,
  val updatedAt: String? = null,
  val role: String? = null
)

/**
 * Wire -> domain. Kept next to the DTO so the two move together.
 *
 * A studio with no name renders as an empty title rather than being dropped:
 * hiding a row the server sent would make the count on screen disagree with the
 * count on the web, and that is much harder to diagnose than a blank label.
 */
fun StudioDto.toDomain(): Studio = Studio(
  id = id,
  slug = slug.orEmpty(),
  name = name.orEmpty(),
  ownerId = ownerId.orEmpty(),
  role = StudioRole.fromWire(role),
  createdAt = createdAt,
  updatedAt = updatedAt
)
