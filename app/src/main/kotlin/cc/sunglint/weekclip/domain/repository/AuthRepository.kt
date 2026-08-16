package cc.sunglint.weekclip.domain.repository

import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.core.session.ProfileSession

/**
 * Turning a finished OAuth round trip into a session.
 *
 * Separate from `SessionRefresher` even though both hit the same GoTrue
 * endpoint, because they answer to different callers with different failure
 * semantics: a refresh that fails may mean "sign the user out", while a failed
 * exchange means "the sign-in did not take" and there was no session to lose.
 * Folding them together would put `RefreshOutcome`'s three-way answer in front
 * of a screen that has no use for it.
 */
interface AuthRepository {

  /**
   * @param verifier the PKCE verifier this process generated before opening the
   *   browser, read back from `SignInFlowStore`.
   * @return the grant. Storing it is the caller's job — `SessionManager` owns
   *   that, and this layer deliberately does not reach into it.
   */
  suspend fun exchangeAuthCode(code: String, verifier: String): AppResult<ProfileSession>
}
