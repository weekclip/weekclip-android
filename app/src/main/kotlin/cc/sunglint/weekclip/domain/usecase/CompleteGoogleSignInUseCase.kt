package cc.sunglint.weekclip.domain.usecase

import cc.sunglint.weekclip.core.auth.SignInFlowStore
import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.core.session.SessionManager
import cc.sunglint.weekclip.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Everything that has to happen after the browser comes back.
 *
 * Take the verifier, trade the code for a grant, hand the grant to the one
 * thing allowed to own sessions. The ViewModel does none of it, because the
 * middle step must not be skippable: a code that reached the app without a
 * matching verifier is a code that belongs to a different sign-in — possibly a
 * different app's (see `PkceChallenge`) — and the only correct response is to
 * refuse it.
 *
 * The intended route is **not** read here. Navigating is the shell's job, and
 * it has to work identically for a user who was already signed in when the link
 * arrived — a path this use case never runs on.
 */
class CompleteGoogleSignInUseCase @Inject constructor(
  private val flowStore: SignInFlowStore,
  private val repository: AuthRepository,
  private val sessionManager: SessionManager
) {

  suspend operator fun invoke(code: String): AppResult<Unit> {
    // Consumes it. A retry after a failed exchange starts a new round trip with
    // a new verifier, because the code it would pair with is spent either way.
    val verifier = flowStore.takeVerifier()
      ?: return AppResult.Failure(AppError.Unauthorized)

    return when (val result = repository.exchangeAuthCode(code = code, verifier = verifier)) {
      is AppResult.Success -> {
        sessionManager.adopt(result.value)
        AppResult.Success(Unit)
      }

      is AppResult.Failure -> result
    }
  }
}
