package cc.sunglint.weekclip.data

import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.core.session.AuthConfig
import cc.sunglint.weekclip.core.session.SessionClock
import cc.sunglint.weekclip.data.remote.auth.SupabaseAuthService
import cc.sunglint.weekclip.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The PKCE exchange against real GoTrue bytes.
 *
 * MockWebServer rather than a fake service, for the reason
 * `SupabaseSessionRefresherTest` gives next door: the layer most likely to be
 * wrong here is the request body's field names — GoTrue wants `auth_code` where
 * OAuth says `code` — and a hand-written fake of the Retrofit interface skips
 * exactly that layer.
 */
class SupabaseAuthRepositoryTest {

  private lateinit var server: MockWebServer
  private lateinit var service: SupabaseAuthService

  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private val config = AuthConfig(supabaseUrl = "https://project.supabase.co", anonKey = "anon")

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    service = Retrofit.Builder()
      .baseUrl(server.url("/auth/v1/"))
      .client(OkHttpClient())
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
      .create(SupabaseAuthService::class.java)
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun repository(authConfig: AuthConfig = config) = SupabaseAuthRepository(
    service = service,
    authConfig = authConfig,
    clock = SessionClock { 1_000 },
    io = UnconfinedTestDispatcher()
  )

  @Test
  fun `sends the grant type and field names GoTrue expects`() = runTest {
    server.enqueue(MockResponse().setBody(GRANT).setHeader("Content-Type", "application/json"))

    repository().exchangeAuthCode(code = "code-1", verifier = "verifier-1")

    val request = server.takeRequest()
    assertEquals("/auth/v1/token?grant_type=pkce", request.path)
    val body = request.body.readUtf8()
    // `auth_code`, not `code`. Getting this wrong produces a 400 that looks
    // exactly like an expired code.
    assertTrue(body, body.contains("\"auth_code\":\"code-1\""))
    assertTrue(body, body.contains("\"code_verifier\":\"verifier-1\""))
  }

  @Test
  fun `a grant response becomes a session`() = runTest {
    server.enqueue(MockResponse().setBody(GRANT).setHeader("Content-Type", "application/json"))

    val result = repository().exchangeAuthCode("code-1", "verifier-1")

    val session = (result as AppResult.Success).value
    assertEquals("access-token-value", session.accessToken)
    assertEquals("refresh-token-value", session.refreshToken)
    // The absolute expiry the grant carried, not one re-derived from device time.
    assertEquals(1_755_300_000L, session.expiresAtEpochSeconds)
    assertEquals("cb19da57-0000-0000-0000-000000000000", session.userId)
  }

  @Test
  fun `a rejected code is a server error, not an expired session`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(400)
        .setBody("""{"error":"invalid_grant","error_description":"invalid flow state"}""")
    )

    val result = repository().exchangeAuthCode("code-1", "verifier-1")

    // GoTrue answers 400 here, not 401. Folding it into Unauthorized would tell
    // the user their session ended — they never had one — and would suppress
    // the retry that actually fixes it.
    assertEquals(AppResult.Failure(AppError.Server(status = 400)), result)
  }

  @Test
  fun `a 200 that is not a grant does not become a half-built session`() = runTest {
    server.enqueue(MockResponse().setBody("""{"ok":true}"""))

    val result = repository().exchangeAuthCode("code-1", "verifier-1")

    assertEquals(AppResult.Failure(AppError.MalformedResponse), result)
  }

  @Test
  fun `a grant with no user is refused`() = runTest {
    server.enqueue(
      MockResponse().setBody(
        """{"access_token":"a","refresh_token":"r","expires_in":3600}"""
      )
    )

    val result = repository().exchangeAuthCode("code-1", "verifier-1")

    // Unlike a refresh, an exchange has no previous session to inherit an id
    // from. A session with no subject is not one this app can act on.
    assertEquals(AppResult.Failure(AppError.MalformedResponse), result)
  }

  @Test
  fun `expires_in is used when the response omits expires_at`() = runTest {
    server.enqueue(
      MockResponse().setBody(
        """{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"u1"}}"""
      )
    )

    val result = repository().exchangeAuthCode("code-1", "verifier-1")

    assertEquals(4_600L, (result as AppResult.Success).value.expiresAtEpochSeconds)
  }

  @Test
  fun `a build with no project key does not reach the network`() = runTest {
    val result = repository(AuthConfig(supabaseUrl = "", anonKey = ""))
      .exchangeAuthCode("code-1", "verifier-1")

    assertTrue(result.toString(), result is AppResult.Failure)
    assertEquals(0, server.requestCount)
  }

  private companion object {
    /**
     * The dev project's grant shape, captured 2026-08-15 (values replaced).
     * `expires_at` in epoch seconds is the field that matters most — it is what
     * stops the app from asking the device what time it is.
     */
    val GRANT = """
      {
        "access_token": "access-token-value",
        "refresh_token": "refresh-token-value",
        "token_type": "bearer",
        "expires_in": 3600,
        "expires_at": 1755300000,
        "user": { "id": "cb19da57-0000-0000-0000-000000000000" }
      }
    """.trimIndent()
  }
}
