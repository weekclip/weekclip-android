package cc.sunglint.weekclip.di

import cc.sunglint.weekclip.BuildConfig
import cc.sunglint.weekclip.core.network.ApiEndpoints
import cc.sunglint.weekclip.core.network.AuthInterceptor
import cc.sunglint.weekclip.core.network.NoSessionTokenProvider
import cc.sunglint.weekclip.core.network.SessionTokenProvider
import cc.sunglint.weekclip.data.remote.WeekclipApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    // The API adds response fields without a version bump (StudioDto's `slug`
    // arrived that way). Strict decoding would make every such addition a crash
    // for every client that has not shipped yet.
    ignoreUnknownKeys = true
    // Absent and null mean the same thing to this API; not writing nulls also
    // keeps request bodies small on mobile links.
    explicitNulls = false
  }

  @Provides
  @Singleton
  fun provideApiEndpoints(): ApiEndpoints = ApiEndpoints.fromBuildConfig()

  @Provides
  @Singleton
  fun provideSessionTokenProvider(): SessionTokenProvider = NoSessionTokenProvider()

  @Provides
  @Singleton
  fun provideOkHttpClient(tokenProvider: SessionTokenProvider): OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor(AuthInterceptor(tokenProvider))
      .apply {
        // BODY level prints bearer tokens and media URLs. Gating on
        // BuildConfig.DEBUG is not belt-and-braces — R8 also strips the whole
        // branch from release, so the interceptor is not merely quiet, it is
        // absent from the shipped binary.
        if (BuildConfig.DEBUG) {
          addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
          )
        }
      }
      // Uploads are handled by a dedicated background engine (PRD-0008 N4), not
      // this client — these timeouts are for ordinary API calls.
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()

  @Provides
  @Singleton
  fun provideRetrofit(
    client: OkHttpClient,
    json: Json,
    endpoints: ApiEndpoints
  ): Retrofit = Retrofit.Builder()
    .baseUrl(endpoints.apiBaseUrlForRetrofit)
    .client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

  @Provides
  @Singleton
  fun provideWeekclipApiService(retrofit: Retrofit): WeekclipApiService =
    retrofit.create(WeekclipApiService::class.java)
}
