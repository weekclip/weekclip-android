package cc.sunglint.weekclip.debug

import cc.sunglint.weekclip.data.remote.auth.SupabaseTokenResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * `grant_type=password` against Supabase GoTrue — **debug source set only**.
 *
 * weekclip's product login is Google OAuth and nothing else (weekclip-web
 * `LoginPage.tsx`). This is not a second product login sneaking in; it is the
 * only way to get a genuine token in front of the session code before the OAuth
 * clients exist (148.5c-b), and it lives here so that the release APK does not
 * contain the endpoint, the request type, or the word `password` in a grant.
 *
 * The dev Supabase project has the email provider enabled — `/auth/v1/settings`
 * reports `"email": true` (checked 2026-08-15) — which is what makes this
 * possible at all. Production is a different project and this code is not in
 * the build that talks to it.
 */
interface DebugSupabaseAuthService {

  @POST("token")
  suspend fun signInWithPassword(
    @Query("grant_type") grantType: String,
    @Body body: PasswordGrantRequest
  ): Response<SupabaseTokenResponse>
}

@Serializable
data class PasswordGrantRequest(
  val email: String,
  @SerialName("password") val password: String
)
