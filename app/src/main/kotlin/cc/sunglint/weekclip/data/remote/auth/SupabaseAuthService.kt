package cc.sunglint.weekclip.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The single Supabase GoTrue call the shipped app makes.
 *
 * Only `grant_type=refresh_token` is here. Sign-in is not: the product's one
 * login is Google OAuth (weekclip-web `LoginPage.tsx`), which needs per-platform
 * OAuth clients that do not exist yet (148.5c-b). The password grant used to
 * exercise this code lives in the **debug source set** and is therefore absent
 * from the release APK rather than merely unreachable in it.
 */
interface SupabaseAuthService {

  @POST("token")
  suspend fun refresh(
    @Query("grant_type") grantType: String,
    @Body body: RefreshTokenRequest
  ): Response<SupabaseTokenResponse>
}

@Serializable
data class RefreshTokenRequest(
  @SerialName("refresh_token") val refreshToken: String
)

/**
 * A GoTrue grant response, as measured against the dev project on 2026-08-15:
 * `access_token`, `refresh_token`, `token_type`, `expires_in`, `expires_at`,
 * `user`, and (for a password grant) `weak_password`.
 *
 * `expires_at` is nullable even though the observed response always carried it.
 * The field is not in GoTrue's documented minimum, and a client that crashes
 * when an optional field disappears is a client that ships a broken build the
 * day the provider trims its payload — [expiresIn] is the fallback.
 */
@Serializable
data class SupabaseTokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("refresh_token") val refreshToken: String,
  @SerialName("expires_at") val expiresAt: Long? = null,
  @SerialName("expires_in") val expiresIn: Long? = null,
  val user: SupabaseUser? = null
)

@Serializable
data class SupabaseUser(
  val id: String
)
