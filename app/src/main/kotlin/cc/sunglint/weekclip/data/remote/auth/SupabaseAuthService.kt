package cc.sunglint.weekclip.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The Supabase GoTrue calls the shipped app makes.
 *
 * Two grants, and they are the whole of the app's relationship with the
 * identity provider:
 *
 * | grant | when |
 * |-------|------|
 * | `pkce` | the user finished Google sign-in and came back with a code |
 * | `refresh_token` | the stored access token is spent |
 *
 * `password` is deliberately absent. The product's one login is Google
 * (weekclip-web `LoginPage.tsx`); the password grant that exercises the session
 * code on a device lives in the **debug source set**, so it is missing from the
 * release APK rather than merely unreachable in it.
 *
 * Both grants go through the `@AuthApi` client, which carries the project key
 * and none of the session interceptors — the mechanism that obtains a
 * credential must not depend on having one (`NetworkModule`).
 */
interface SupabaseAuthService {

  @POST("token")
  suspend fun refresh(
    @Query("grant_type") grantType: String,
    @Body body: RefreshTokenRequest
  ): Response<SupabaseTokenResponse>

  /**
   * Trades the authorization code from the redirect for a real grant.
   *
   * The field names are GoTrue's, not the OAuth spec's: it wants `auth_code`
   * where RFC 6749 says `code`. Read out of `@supabase/auth-js`
   * (`GoTrueClient.js`, `POST /token?grant_type=pkce`), which is the client
   * already talking to these same projects from weekclip-web.
   */
  @POST("token")
  suspend fun exchangeAuthCode(
    @Query("grant_type") grantType: String,
    @Body body: PkceGrantRequest
  ): Response<SupabaseTokenResponse>
}

@Serializable
data class RefreshTokenRequest(
  @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class PkceGrantRequest(
  @SerialName("auth_code") val authCode: String,
  @SerialName("code_verifier") val codeVerifier: String
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
