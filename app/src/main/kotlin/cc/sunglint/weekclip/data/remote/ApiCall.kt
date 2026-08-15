package cc.sunglint.weekclip.data.remote

import cc.sunglint.weekclip.core.network.ApiEnvelope
import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.core.result.AppResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The single place transport failures become [AppError].
 *
 * Every repository routes through this, so there is exactly one answer to "what
 * does a timeout look like to the UI" — and one place to change it. Repositories
 * that each write their own `try/catch` drift within a week.
 */
object ApiCall {

  /**
   * Runs [block], unwraps the API envelope, and folds everything that can go
   * wrong into [AppResult].
   *
   * @param transform maps the envelope's `data` payload to the domain type. It
   *   is only called on a 2xx response that actually carried `data`.
   */
  suspend fun <D, T> envelope(
    json: Json,
    block: suspend () -> Response<ApiEnvelope<D>>,
    transform: (D) -> T
  ): AppResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
      val data = response.body()?.data
      if (data == null) {
        // 2xx with no `data` is not "empty" — an empty list still arrives as
        // `{"data":{"items":[]}}`. A missing envelope means the response was
        // not what this endpoint promises.
        AppResult.Failure(AppError.MalformedResponse)
      } else {
        AppResult.Success(transform(data))
      }
    } else {
      AppResult.Failure(toError(response.code(), response.errorBody()?.string(), json))
    }
  } catch (e: SocketTimeoutException) {
    // Must be caught before IOException — it is a subclass.
    AppResult.Failure(AppError.Timeout)
  } catch (e: UnknownHostException) {
    // DNS did not resolve: no network, or captive portal. Same subclass caveat.
    AppResult.Failure(AppError.Offline)
  } catch (e: IOException) {
    AppResult.Failure(AppError.Offline)
  } catch (e: SerializationException) {
    AppResult.Failure(AppError.MalformedResponse)
  }

  /**
   * Status code + error envelope -> [AppError].
   *
   * 403 folds into [AppError.Unauthorized] with 401. They differ server-side
   * (no session vs. wrong session), but the app's move is the same: get a
   * session. Splitting them would give the UI a branch with no distinct action.
   */
  internal fun toError(status: Int, rawBody: String?, json: Json): AppError = when (status) {
    401, 403 -> AppError.Unauthorized
    404 -> AppError.NotFound
    else -> {
      val body = rawBody?.takeIf { it.isNotBlank() }?.let { text ->
        runCatching { json.decodeFromString<ApiEnvelope<Unit>>(text) }.getOrNull()
      }
      AppError.Server(
        status = status,
        code = body?.error?.code,
        message = body?.error?.message
      )
    }
  }
}
