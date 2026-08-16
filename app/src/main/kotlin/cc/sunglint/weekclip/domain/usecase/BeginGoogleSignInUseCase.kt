package cc.sunglint.weekclip.domain.usecase

import cc.sunglint.weekclip.core.auth.PkceChallenge
import cc.sunglint.weekclip.core.auth.SignInFlowStore
import cc.sunglint.weekclip.core.auth.SupabaseOAuth
import cc.sunglint.weekclip.core.session.AuthConfig
import javax.inject.Inject

/**
 * Everything that has to happen before the browser opens.
 *
 * A use case rather than three calls in the ViewModel because the **order** is
 * load-bearing and getting it wrong is not visible in a screenshot: the
 * verifier has to be on disk *before* the URL derived from it can be handed to
 * the browser. Reversed, a fast redirect — or a process death — can bring the
 * app back holding a code it has no verifier for, which reads to the user as
 * "Google worked and weekclip lost it".
 */
class BeginGoogleSignInUseCase @Inject constructor(
  private val authConfig: AuthConfig,
  private val flowStore: SignInFlowStore
) {

  /**
   * @return the URL to open in the system browser, or null when this build has
   *   no Supabase project compiled in. Null is a real state, not a guard: it is
   *   what a release build looked like until the production anon key landed in
   *   the ledger, and the login screen says so rather than opening a browser at
   *   nothing.
   */
  suspend operator fun invoke(): String? {
    val challenge = PkceChallenge.generate()
    val url = SupabaseOAuth.authorizeUrl(config = authConfig, challenge = challenge)
      ?: return null

    flowStore.putVerifier(challenge.verifier)
    return url
  }
}
