package cc.sunglint.weekclip.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * What actually goes out on the wire.
 *
 * Asserted against a real socket rather than against a mocked `Chain`: the
 * header is read back from the request MockWebServer received, so the test
 * cannot pass because the interceptor built a request nobody sent.
 */
class AuthInterceptorTest {

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

  private fun call(path: String, credentials: SessionCredentialProvider): String? {
    server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(credentials)).build()
    client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
    return server.takeRequest().getHeader("Authorization")
  }

  @Test
  fun `a profile request carries the profile bearer`() {
    val header = call("/api/v1/studios", FakeCredentialProvider(profile = "token-1"))

    assertEquals("Bearer token-1", header)
  }

  @Test
  fun `a guest share request does not carry the profile bearer`() {
    // The failure this prevents is concrete. weekclip-api verifies
    // `/share/:token/*` with the share link's own HMAC key
    // (`share-link-session.ts`), not with Supabase's JWKS — so a profile JWT
    // sent here is rejected, and a signed-in user would be told they cannot
    // view a link that works fine in a browser.
    val header = call("/api/v1/share/tok/media", FakeCredentialProvider(profile = "token-1"))

    assertNull(header)
  }

  @Test
  fun `no session means no header at all`() {
    // Not `Bearer null`: that is a malformed credential and comes back 400,
    // which hides "not logged in" behind "bad request".
    assertNull(call("/api/v1/studios", FakeCredentialProvider(profile = null)))
  }

  @Test
  fun `a blank credential is treated as no credential`() {
    assertNull(call("/api/v1/studios", FakeCredentialProvider(profile = "")))
  }
}

class FakeCredentialProvider(
  private val profile: String?,
  private val afterUnauthorized: (String?) -> String? = { null }
) : SessionCredentialProvider {
  var unauthorizedCalls = 0
    private set

  override fun credentialFor(path: String): String? = when (SessionAxis.of(path)) {
    SessionAxis.Profile -> profile
    SessionAxis.Guest -> null
  }

  override fun credentialAfterUnauthorized(path: String, failedCredential: String?): String? {
    unauthorizedCalls++
    return when (SessionAxis.of(path)) {
      SessionAxis.Profile -> afterUnauthorized(failedCredential)
      SessionAxis.Guest -> null
    }
  }
}
