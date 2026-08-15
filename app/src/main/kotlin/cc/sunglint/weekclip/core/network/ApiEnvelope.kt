package cc.sunglint.weekclip.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The API's canonical response envelope.
 *
 * Source of truth: weekclip-api `src/platform/http/envelope.ts`.
 *
 *   success -> { "data": T,                      "meta": { "traceId": "..." } }
 *   failure -> { "error": { "code", "message" }, "traceId": "..." }
 *
 * Note the asymmetry — `traceId` sits under `meta` on success and at the top
 * level on failure. That is the server's shape, not a transcription slip.
 */
@Serializable
data class ApiEnvelope<T>(
  val data: T? = null,
  val meta: ApiMeta? = null,
  val error: ApiErrorBody? = null,
  @SerialName("traceId") val traceId: String? = null
)

@Serializable
data class ApiMeta(
  val traceId: String? = null
)

@Serializable
data class ApiErrorBody(
  val code: String? = null,
  val message: String? = null
)

/**
 * List payloads are nested one level further: `{ "data": { "items": [...] } }`.
 * `listStudiosForProfile` in weekclip-api returns `{ items }`, and the web
 * client reads `["data","items"]` first for that reason.
 */
@Serializable
data class ItemsPayload<T>(
  val items: List<T> = emptyList()
)
