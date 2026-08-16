package cc.sunglint.weekclip.core.auth

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one slot an OAuth redirect lands in, between the Activity that receives
 * it and the ViewModel that acts on it.
 *
 * ### Why not a Flow
 *
 * Because the timing is the point, and a Flow would lose it. The login screen
 * has to answer a question that only makes sense at one instant — *"we are back
 * in the foreground; did a redirect come with us, or did the user just walk out
 * of the browser?"* — and the two answers lead to opposite states: exchange the
 * code, or drop the spinner and go back to the button.
 *
 * The Android lifecycle already orders those events for us:
 *
 * ```
 * onNewIntent  →  onRestart  →  onStart  →  onResume
 *   ^ offer()                                 ^ take()
 * ```
 *
 * `onNewIntent` runs **before** `onResume`, so by the time the screen asks, the
 * redirect is either in this slot or it never existed. A `SharedFlow` collected
 * in `viewModelScope` gives the same value eventually, which is not the same
 * thing: "eventually" arrives after the resume check has already concluded the
 * user cancelled, and the user watches a completed Google sign-in turn back
 * into a login button.
 *
 * ### Why a singleton and not `SavedStateHandle`
 *
 * The Activity receives the intent; a ViewModel two composables down needs it.
 * Passing it through composition means it arrives on the next recomposition —
 * again, after the resume check.
 *
 * Holding it in a process-wide singleton is safe *because* it is drained on
 * read: nothing here survives a process death, and nothing needs to. The
 * verifier does, and that is on disk (`SignInFlowStore`).
 */
@Singleton
class AuthRedirectBus @Inject constructor() {

  private val pending = AtomicReference<OAuthCallback?>(null)

  /**
   * Ignores [OAuthCallback.NotACallback] so that the caller can hand over every
   * incoming intent without having to classify them first — and, more
   * importantly, so an ordinary launcher tap cannot overwrite a redirect that
   * has not been read yet.
   */
  fun offer(callback: OAuthCallback) {
    if (callback is OAuthCallback.NotACallback) return
    pending.set(callback)
  }

  /** Reads and clears. Never returns the same redirect twice. */
  fun take(): OAuthCallback? = pending.getAndSet(null)
}
