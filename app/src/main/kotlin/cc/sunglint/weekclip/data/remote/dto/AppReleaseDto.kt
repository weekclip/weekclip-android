package cc.sunglint.weekclip.data.remote.dto

import cc.sunglint.weekclip.domain.model.AppReleasePolicy
import kotlinx.serialization.Serializable

/**
 * `GET /app/version`, exactly as weekclip-api's `buildAppReleasePolicy` emits
 * it. Both platforms are returned; this client reads its own.
 *
 * Every field is nullable-with-a-default, and here that matters more than
 * usual: this is the one endpoint that can stop the app from running, and it is
 * read before anything else. A strict decoder would turn a server-side field
 * addition into a crash on launch, on the exact call that exists to keep the
 * app healthy.
 */
@Serializable
data class AppReleaseDto(
  val android: AppPlatformPolicyDto? = null,
  val ios: AppPlatformPolicyDto? = null
)

@Serializable
data class AppPlatformPolicyDto(
  val minimumBuild: Int? = null,
  val storeUrl: String? = null
)

/**
 * A missing `minimumBuild` becomes 0 — block nobody. The server already fails
 * open for an absent config; this keeps the client from failing closed on a
 * response shape it did not expect.
 */
fun AppPlatformPolicyDto?.toDomain(): AppReleasePolicy = AppReleasePolicy(
  minimumBuild = this?.minimumBuild ?: 0,
  storeUrl = this?.storeUrl?.takeIf { it.isNotBlank() }
)
