package cc.sunglint.weekclip.di

import cc.sunglint.weekclip.data.repository.DefaultAppReleaseRepository
import cc.sunglint.weekclip.data.repository.DefaultStudioRepository
import cc.sunglint.weekclip.data.repository.SupabaseAuthRepository
import cc.sunglint.weekclip.domain.repository.AppReleaseRepository
import cc.sunglint.weekclip.domain.repository.AuthRepository
import cc.sunglint.weekclip.domain.repository.StudioRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Interface -> implementation bindings.
 *
 * `@Binds` on an abstract class rather than `@Provides` on an object: it
 * compiles to a cast instead of a generated factory method, and it cannot
 * accidentally grow a body that does construction work the injector should own
 * (android-architecture skill §2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

  @Binds
  @Singleton
  abstract fun bindStudioRepository(impl: DefaultStudioRepository): StudioRepository

  @Binds
  @Singleton
  abstract fun bindAppReleaseRepository(impl: DefaultAppReleaseRepository): AppReleaseRepository

  @Binds
  @Singleton
  abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository
}
