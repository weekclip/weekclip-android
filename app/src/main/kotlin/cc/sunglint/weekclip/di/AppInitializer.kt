package cc.sunglint.weekclip.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope

/**
 * Something that has to run once, at process start, before any screen exists.
 *
 * The set is declared empty in `main` and contributed to elsewhere. Right now
 * the only contributor is the debug source set's auto sign-in, and that is the
 * point of the indirection: `main` has no reference to a debug-only class, so
 * the release APK contains neither the initializer nor a branch that skips it.
 * The alternative — `if (BuildConfig.DEBUG) DebugAutoSignIn(...)` in
 * `Application.onCreate` — would not compile in a release build at all, since
 * the class is not on that source path.
 */
fun interface AppInitializer {
  fun initialize(scope: CoroutineScope)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppInitializerModule {

  /**
   * `@Multibinds` rather than an empty `@Provides`: it declares the set may be
   * empty without materialising a second binding that a contributor would then
   * have to fight with.
   */
  @Multibinds
  abstract fun initializers(): Set<AppInitializer>
}
