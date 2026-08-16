package cc.sunglint.weekclip.debug

import cc.sunglint.weekclip.BuildConfig
import cc.sunglint.weekclip.di.AuthApi
import cc.sunglint.weekclip.ui.auth.DebugSignInAction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Contributes the debug-only sign-in to the graph.
 *
 * Present only in the debug source set, so a release build has no
 * [DebugSignInAction] at all and `LoginViewModel` reports a null label — the
 * button is absent from the binary, not merely hidden in it.
 *
 * Reuses the `@AuthApi` Retrofit from `NetworkModule` — the client deliberately
 * kept clear of the session interceptors, which is exactly right for a call
 * whose whole job is to produce the session.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugSessionModule {

  @Provides
  @Singleton
  fun provideDebugSupabaseAuthService(@AuthApi retrofit: Retrofit): DebugSupabaseAuthService =
    retrofit.create(DebugSupabaseAuthService::class.java)

  /**
   * Contributed only when `local.properties` actually supplies credentials.
   *
   * `@ElementsIntoSet` rather than `@IntoSet` so the set can be empty: a button
   * that is guaranteed to fail is worse than no button, and it makes the state
   * of the checkout visible on the screen. It is also what lets `maestro/`
   * branch — a flow can ask whether the affordance is there instead of
   * discovering that it is inert.
   */
  @Provides
  @ElementsIntoSet
  fun provideDebugPasswordSignIn(action: DebugPasswordSignIn): Set<DebugSignInAction> =
    if (BuildConfig.DEBUG_SIGN_IN_EMAIL.isBlank() || BuildConfig.DEBUG_SIGN_IN_PASSWORD.isBlank()) {
      emptySet()
    } else {
      setOf(action)
    }
}
