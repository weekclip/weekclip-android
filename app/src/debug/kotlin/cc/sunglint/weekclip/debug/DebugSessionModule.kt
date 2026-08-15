package cc.sunglint.weekclip.debug

import cc.sunglint.weekclip.di.AppInitializer
import cc.sunglint.weekclip.di.AuthApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Contributes the debug-only sign-in to the graph.
 *
 * Present only in the debug source set, so a release build has no
 * [AppInitializer] at all and `WeekclipApplication` iterates an empty set.
 * Reuses the `@AuthApi` Retrofit from `NetworkModule` — the client that is
 * deliberately kept clear of the session interceptors, which is exactly right
 * for a call whose whole job is to produce the session.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugSessionModule {

  @Provides
  @Singleton
  fun provideDebugSupabaseAuthService(@AuthApi retrofit: Retrofit): DebugSupabaseAuthService =
    retrofit.create(DebugSupabaseAuthService::class.java)

  @Provides
  @IntoSet
  fun provideDebugAutoSignIn(initializer: DebugAutoSignIn): AppInitializer = initializer
}
