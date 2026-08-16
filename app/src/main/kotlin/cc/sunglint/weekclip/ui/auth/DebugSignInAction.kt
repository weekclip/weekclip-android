package cc.sunglint.weekclip.ui.auth

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * A way into the app that is not Google, contributed only by builds that have
 * one.
 *
 * The set is **empty in `main`**. That is the whole design: a release APK
 * contains no implementation, no binding, and no branch that skips one, so the
 * login screen's debug button is not hidden — it does not exist. The
 * alternative, `if (BuildConfig.DEBUG)`, would not even compile, because the
 * class it names is not on the release source path.
 *
 * This replaces the `AppInitializer` set that used to sign in automatically at
 * process start (148.5c). Automatic sign-in was right when there was no login
 * screen to reach; with one, it walks straight past the gate this task exists
 * to build, and a gate that the development build never sees is a gate nobody
 * is testing.
 *
 * The "only if nothing is stored" property that made the old initializer a real
 * check is not lost — it moved somewhere better. The button is only reachable
 * from the login screen, and the login screen is only reachable when
 * `SessionManager` reports `SignedOut`. A restored session therefore never sees
 * it, which is the same assertion made structurally instead of by an `if`.
 */
interface DebugSignInAction {

  /**
   * What to put on the button.
   *
   * Carried here rather than as an `R.string` in `main`, and that is not
   * fussiness: a string resource referenced by release code ships in the
   * release APK even when nothing can reach it. Measured — `login_debug_password`
   * survived into `app-release-unsigned.apk` the first time this was written
   * that way. A debug-only affordance should leave nothing behind, including a
   * line for a translator to wonder about.
   */
  val label: String

  /** @return true when a session was adopted. */
  suspend fun signIn(): Boolean
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugSignInModule {

  /**
   * `@Multibinds` rather than an empty `@Provides`: it declares that the set may
   * be empty without materialising a second binding for a contributor to fight
   * with.
   */
  @Multibinds
  abstract fun debugSignInActions(): Set<DebugSignInAction>
}
