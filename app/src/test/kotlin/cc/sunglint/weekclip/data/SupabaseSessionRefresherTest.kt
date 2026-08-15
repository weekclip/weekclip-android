package cc.sunglint.weekclip.data

import cc.sunglint.weekclip.core.session.ProfileSession
import cc.sunglint.weekclip.core.session.RefreshOutcome
import cc.sunglint.weekclip.core.session.SessionClock
import cc.sunglint.weekclip.data.remote.auth.SupabaseAuthService
import cc.sunglint.weekclip.data.repository.SupabaseSessionRefresher
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
 * The refresher against real GoTrue bytes.
 *
 * The success payload below is the actual response shape from the dev Supabase
 * project, captured on 2026-08-15 (values replaced). Decoding a hand-written
 * approximation would prove nothing about the field that matters most —
 * `expires_at`, which is what stops the app from asking the device what time it
 * is.
 */
class SupabaseSessionRefresherTest {

  private lateinit var server: MockWebServer
  private lateinit var service: SupabaseAuthService

  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private val clock = SessionClock { 1_000 }
  private val previous = ProfileSession("old-access", "old-refresh", 900, "user-1")

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

  private fun refresher() = SupabaseSessionRefresher(service, clock)

  @Test
  fun `a grant response becomes a session`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """
        {
          "access_token": "new-access",
          "token_type": "bearer",
          "expires_in": 3600,
          "expires_at": 1786787949,
          "refresh_token": "new-refresh",
          "user": { "id": "user-1", "email": "adam@weekclip.com" }
        }
        """.trimIndent()
      )
    )

    val outcome = refresher().refresh(previous)

    assertEquals(
      RefreshOutcome.Refreshed(ProfileSession("new-access", "new-refresh", 1786787949, "user-1")),
      outcome
    )
  }

  @Test
  fun `the request is a refresh_token grant carrying the stored refresh token`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token":"a","refresh_token":"r","expires_at":2000}"""))

    refresher().refresh(previous)

    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/auth/v1/token?grant_type=refresh_token", request.path)
    assertTrue(request.body.readUtf8().contains("old-refresh"))
  }

  @Test
  fun `an absent expires_at falls back to expires_in on the device clock`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200)
        .setBody("""{"access_token":"a","refresh_token":"r","expires_in":3600}""")
    )

    val outcome = refresher().refresh(previous)

    assertEquals(1_000 + 3_600, (outcome as RefreshOutcome.Refreshed).session.expiresAtEpochSeconds)
  }

  @Test
  fun `a refresh response without a user keeps the previous identity`() = runTest {
    // A refresh token belongs to exactly one user by construction, so the id
    // cannot have changed — and dropping it would strand the app in SignedIn
    // with nobody signed in.
    server.enqueue(
      MockResponse().setResponseCode(200)
        .setBody("""{"access_token":"a","refresh_token":"r","expires_at":2000}""")
    )

    val outcome = refresher().refresh(previous)

    assertEquals("user-1", (outcome as RefreshOutcome.Refreshed).session.userId)
  }

  @Test
  fun `400 refresh_token_not_found is a rejection, not a network blip`() = runTest {
    // GoTrue answers 400 here, not 401. Treating only 401 as fatal would leave
    // a permanently dead session retrying on every screen forever.
    server.enqueue(
      MockResponse().setResponseCode(400)
        .setBody("""{"code":400,"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token"}""")
    )

    assertEquals(RefreshOutcome.Rejected, refresher().refresh(previous))
  }

  @Test
  fun `401 and 403 are rejections`() = runTest {
    listOf(401, 403).forEach { status ->
      server.enqueue(MockResponse().setResponseCode(status))
      assertEquals("status $status", RefreshOutcome.Rejected, refresher().refresh(previous))
    }
  }

  @Test
  fun `a 500 is transient and must not sign the user out`() = runTest {
    server.enqueue(MockResponse().setResponseCode(500))

    assertEquals(RefreshOutcome.Unavailable, refresher().refresh(previous))
  }

  @Test
  fun `a dropped connection is transient`() = runTest {
    server.shutdown()

    assertEquals(RefreshOutcome.Unavailable, refresher().refresh(previous))
  }

  @Test
  fun `a 200 that carries no token is transient rather than a sign-out`() = runTest {
    // Our own parsing failing is not evidence that the user's session ended.
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"unexpected":"shape"}"""))

    assertEquals(RefreshOutcome.Unavailable, refresher().refresh(previous))
  }
}
