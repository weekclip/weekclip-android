package cc.sunglint.weekclip.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

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

// An @ApplicationScope CoroutineScope used to live here, for the process-start
// initializers that no longer exist (see WeekclipApplication). Nothing injects
// it now, and a provider nobody asks for is a decision made in advance of the
// requirement — Phase 5's upload engine will state its own lifetime, and it may
// well be WorkManager's rather than a scope.
