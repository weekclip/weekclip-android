package cc.sunglint.weekclip.core.result

/**
 * Every failure the UI is allowed to see.
 *
 * The point of a closed set is that a screen can `when` over it exhaustively
 * and the compiler catches a new case. Raw `Throwable`s leak OkHttp and
 * kotlinx-serialization types into the UI layer, which the architecture skill
 * forbids for good reason: a `SerializationException` is not something a user
 * can act on.
 *
 * Deliberately carries no user-facing copy. Strings live in `strings.xml` so
 * they are translatable, and — since PRD-0008 D3 forbids payment wording — so
 * that `scripts/check-no-payment-strings.sh` has one place to look.
 */
sealed interface AppError {
  /** No usable network. Retrying later is the sensible action. */
  data object Offline : AppError

  /** The request went out and nothing came back in time. */
  data object Timeout : AppError

  /** 401/403 — the session is missing or no longer valid. */
  data object Unauthorized : AppError

  /** 404 — the studio/media is gone, or was never visible to this profile. */
  data object NotFound : AppError

  /** Any other non-2xx. `code` is the API's own error code when it sent one. */
  data class Server(
    val status: Int,
    val code: String? = null,
    val message: String? = null
  ) : AppError

  /**
   * The response was 2xx but did not match the contract — a missing `data`
   * envelope, or JSON that would not deserialize. Distinct from [Server]
   * because it means *we* are wrong, and it should be loud in logs.
   */
  data object MalformedResponse : AppError

  /** Nothing above fits. Carries the cause so it can be logged, not shown. */
  data class Unexpected(val cause: Throwable) : AppError
}

/**
 * A success/failure pair over [AppError].
 *
 * Not `kotlin.Result`: that one carries a `Throwable` (so it cannot express a
 * closed error set), and returning it from a suspend function is a documented
 * footgun — the compiler boxes it inconsistently across inline boundaries.
 */
sealed interface AppResult<out T> {
  data class Success<out T>(val value: T) : AppResult<T>
  data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
  is AppResult.Success -> AppResult.Success(transform(value))
  is AppResult.Failure -> this
}
