package cc.sunglint.weekclip.data.remote

import cc.sunglint.weekclip.core.network.ApiEnvelope
import cc.sunglint.weekclip.core.network.ItemsPayload
import cc.sunglint.weekclip.data.remote.dto.StudioDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * weekclip-api (`service-api.*`) endpoints the app calls.
 *
 * Returns `Response<T>` rather than the body directly. Both work with `suspend`,
 * but the bare-body form throws `HttpException` on 4xx/5xx, and the status code
 * is the only thing that distinguishes "log in again" (401) from "this studio
 * is gone" (404) from "the server is unwell" (500). Fishing that back out of an
 * exception is strictly worse than never losing it (android-retrofit skill §4).
 *
 * One endpoint is here on purpose. The other 57 in the inventory arrive with
 * the screens that call them (PRD-0008 Phase 5) — an interface full of methods
 * nothing calls cannot be wrong in any way the compiler will tell us about.
 */
interface WeekclipApiService {

  /** `{ "data": { "items": [...] }, "meta": { "traceId" } }` */
  @GET("studios")
  suspend fun getStudios(): Response<ApiEnvelope<ItemsPayload<StudioDto>>>
}
