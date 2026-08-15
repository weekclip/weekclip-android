package cc.sunglint.weekclip.data

import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.data.remote.WeekclipApiService
import cc.sunglint.weekclip.data.repository.DefaultStudioRepository
import cc.sunglint.weekclip.domain.model.StudioRole
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
 * The repository against real bytes on a real socket.
 *
 * A hand-rolled fake of [WeekclipApiService] would skip the layer most likely
 * to be wrong — the envelope. weekclip-api nests list payloads twice
 * (`data.items`) and puts `traceId` in different places on success and failure;
 * only a decode of the actual JSON can catch a mistake there.
 *
 * The JSON below is copied from the shape `presentStudioMembership` emits
 * (weekclip-api `src/studios/application/present-studio-membership.ts`). If the
 * server changes it, this test is where that should hurt.
 */
class StudioRepositoryContractTest {

  private lateinit var server: MockWebServer
  private lateinit var repository: DefaultStudioRepository

  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()

    val service = Retrofit.Builder()
      .baseUrl(server.url("/api/v1/"))
      .client(OkHttpClient())
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
      .create(WeekclipApiService::class.java)

    repository = DefaultStudioRepository(service, json, UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun enqueue(code: Int, body: String) {
    server.enqueue(
      MockResponse()
        .setResponseCode(code)
        .setHeader("content-type", "application/json")
        .setBody(body)
    )
  }

  @Test
  fun `decodes the nested data-items envelope into domain studios`() = runTest {
    enqueue(
      200,
      """
      {
        "data": {
          "items": [
            {
              "id": "st_1",
              "slug": "family",
              "name": "Family",
              "ownerId": "pr_1",
              "createdAt": "2026-08-01T00:00:00.000Z",
              "updatedAt": "2026-08-10T00:00:00.000Z",
              "role": "Owner"
            }
          ]
        },
        "meta": { "traceId": "trace-1" }
      }
      """.trimIndent()
    )

    val result = repository.getStudios()

    assertTrue(result is AppResult.Success)
    val studios = (result as AppResult.Success).value
    assertEquals(1, studios.size)
    assertEquals("st_1", studios[0].id)
    assertEquals("Family", studios[0].name)
    // The wire sends "Owner" capitalised; the domain must not carry wire casing.
    assertEquals(StudioRole.OWNER, studios[0].role)

    // Retrofit drops the last path segment when the base URL has no trailing
    // slash. Asserting the path is what stops that from regressing silently
    // into requests against /api/studios.
    assertEquals("/api/v1/studios", server.takeRequest().path)
  }

  @Test
  fun `an empty items array is a success, not an error`() = runTest {
    enqueue(200, """{"data":{"items":[]},"meta":{"traceId":"t"}}""")

    val result = repository.getStudios()

    assertEquals(AppResult.Success(emptyList<Nothing>()), result)
  }

  @Test
  fun `a 2xx with no data envelope is malformed, not empty`() = runTest {
    enqueue(200, """{"meta":{"traceId":"t"}}""")

    val result = repository.getStudios()

    assertEquals(AppResult.Failure(AppError.MalformedResponse), result)
  }

  @Test
  fun `401 becomes Unauthorized`() = runTest {
    enqueue(401, """{"error":{"code":"unauthorized","message":"no session"},"traceId":"t"}""")

    assertEquals(AppResult.Failure(AppError.Unauthorized), repository.getStudios())
  }

  @Test
  fun `403 folds into Unauthorized because the app's move is the same`() = runTest {
    enqueue(403, """{"error":{"code":"forbidden","message":"nope"},"traceId":"t"}""")

    assertEquals(AppResult.Failure(AppError.Unauthorized), repository.getStudios())
  }

  @Test
  fun `404 becomes NotFound`() = runTest {
    enqueue(404, """{"error":{"code":"not_found","message":"gone"},"traceId":"t"}""")

    assertEquals(AppResult.Failure(AppError.NotFound), repository.getStudios())
  }

  @Test
  fun `a 500 keeps the server's own error code so logs can be correlated`() = runTest {
    enqueue(500, """{"error":{"code":"internal","message":"boom"},"traceId":"t"}""")

    assertEquals(
      AppResult.Failure(AppError.Server(status = 500, code = "internal", message = "boom")),
      repository.getStudios()
    )
  }

  @Test
  fun `a 500 with an unparseable body still reports the status`() = runTest {
    enqueue(500, "<html>gateway blew up</html>")

    assertEquals(
      AppResult.Failure(AppError.Server(status = 500, code = null, message = null)),
      repository.getStudios()
    )
  }

  @Test
  fun `a dead connection becomes Offline rather than an exception`() = runTest {
    // No enqueued response and the server is gone: the socket fails, which is
    // what a phone with no signal produces.
    server.shutdown()

    val result = repository.getStudios()

    assertTrue("expected Offline, got $result", result == AppResult.Failure(AppError.Offline))
  }

  @Test
  fun `unknown roles degrade to UNKNOWN instead of failing the whole list`() = runTest {
    enqueue(
      200,
      """{"data":{"items":[{"id":"st_9","role":"Archivist"}]},"meta":{"traceId":"t"}}"""
    )

    val result = repository.getStudios()

    assertTrue(result is AppResult.Success)
    assertEquals(StudioRole.UNKNOWN, (result as AppResult.Success).value.single().role)
  }
}
