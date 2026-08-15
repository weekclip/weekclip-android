package cc.sunglint.weekclip.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The 401 path, end to end over a socket.
 *
 * This is the half of session handling that clock arithmetic cannot cover: a
 * token revoked server-side, or a device whose clock is simply wrong, looks
 * valid right up until the server says otherwise.
 */
class SessionAuthenticatorTest {

  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun client(credentials: SessionCredentialProvider) = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor(credentials))
    .authenticator(SessionAuthenticator(credentials))
    .build()

  @Test
  fun `a 401 is renewed and the request is replayed once`() {
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    val credentials = FakeCredentialProvider(profile = "stale", afterUnauthorized = { "fresh" })

    val response = client(credentials)
      .newCall(Request.Builder().url(server.url("/api/v1/studios")).build())
      .execute()

    assertEquals(200, response.code)
    response.close()
    assertEquals("Bearer stale", server.takeRequest().getHeader("Authorization"))
    assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
    assertEquals(1, credentials.unauthorizedCalls)
  }

  @Test
  fun `a 401 that cannot be renewed is surfaced instead of retried`() {
    server.enqueue(MockResponse().setResponseCode(401))
    val credentials = FakeCredentialProvider(profile = "stale", afterUnauthorized = { null })

    val response = client(credentials)
      .newCall(Request.Builder().url(server.url("/api/v1/studios")).build())
      .execute()

    assertEquals(401, response.code)
    response.close()
    assertEquals(1, server.requestCount)
  }

  @Test
  fun `a renewal that returns the same credential is not replayed`() {
    // Reachable: when another request refreshed first, the manager hands back
    // what is stored — which may be the very token that just failed.
    server.enqueue(MockResponse().setResponseCode(401))
    val credentials = FakeCredentialProvider(profile = "stale", afterUnauthorized = { it })

    val response = client(credentials)
      .newCall(Request.Builder().url(server.url("/api/v1/studios")).build())
      .execute()

    assertEquals(401, response.code)
    response.close()
    assertEquals(1, server.requestCount)
  }

  @Test
  fun `a retry that also gets 401 stops rather than looping`() {
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(MockResponse().setResponseCode(401))
    var issued = 0
    val credentials = FakeCredentialProvider(
      profile = "stale",
      afterUnauthorized = { "fresh-${issued++}" }
    )

    val response = client(credentials)
      .newCall(Request.Builder().url(server.url("/api/v1/studios")).build())
      .execute()

    assertEquals(401, response.code)
    response.close()
    assertEquals("the authenticator kept renewing instead of giving up", 2, server.requestCount)
  }

  @Test
  fun `a 401 on the guest surface is not renewed`() {
    // A share session is minted by entering the link's password and expires on
    // its own schedule. There is no token exchange that could renew it, so
    // retrying would just spend another round trip to be told the same thing.
    server.enqueue(MockResponse().setResponseCode(401))
    val credentials = FakeCredentialProvider(profile = "token", afterUnauthorized = { "fresh" })

    val response = client(credentials)
      .newCall(Request.Builder().url(server.url("/api/v1/share/tok/media")).build())
      .execute()

    assertEquals(401, response.code)
    response.close()
    assertEquals(1, server.requestCount)
  }
}
