package cc.sunglint.weekclip.di

import cc.sunglint.weekclip.core.network.ApiEndpoints
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    // Uploads are handled by a dedicated background engine (PRD-0008 N4), not
    // this client — these timeouts are for ordinary API calls.
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  @Provides
  @Singleton
  fun provideApiEndpoints(): ApiEndpoints = ApiEndpoints.UNCONFIGURED
}
