package cc.sunglint.weekclip.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Dispatchers are injected, never referenced as `Dispatchers.IO` inside a class.
 *
 * The reason is testability, and it is concrete: a repository that hardcodes
 * `Dispatchers.IO` cannot be driven by `runTest`'s scheduler, so its test
 * either sleeps or flakes. Injecting lets the test pass `UnconfinedTestDispatcher`
 * and stay deterministic (kotlin-concurrency skill).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Work that outlives every screen — see [ApplicationScopeModule]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

  @Provides
  @IoDispatcher
  fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

  @Provides
  @DefaultDispatcher
  fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

  /**
   * A scope tied to the process, for the handful of jobs that must not die with
   * a screen.
   *
   * `SupervisorJob` so one failing initializer does not cancel the others —
   * with a plain `Job`, an [AppInitializer] that throws would take down every
   * subsequent piece of application-scoped work for the life of the process.
   */
  @Provides
  @Singleton
  @ApplicationScope
  fun provideApplicationScope(
    @DefaultDispatcher dispatcher: CoroutineDispatcher
  ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
