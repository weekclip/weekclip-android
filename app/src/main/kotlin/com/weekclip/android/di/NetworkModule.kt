package com.weekclip.android.di

import android.content.Context
import com.weekclip.android.auth.getAccessToken
import com.weekclip.android.studio.api.StudioApiClient
import com.weekclip.android.studio.api.StudioApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.weekclip.android.BuildConfig
import javax.inject.Singleton

private const val API_BASE_URL = "https://dev-service-api.weekclip.com/api/v1/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  @Provides
  @Singleton
  fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
    val interceptor = HttpLoggingInterceptor()
    interceptor.level = if (BuildConfig.DEBUG) {
      HttpLoggingInterceptor.Level.BODY
    } else {
      HttpLoggingInterceptor.Level.NONE
    }
    return interceptor
  }

  @Provides
  @Singleton
  fun provideAuthInterceptor(): Interceptor {
    return Interceptor { chain ->
      val originalRequest = chain.request()
      val token = getAccessToken()

      val newRequest = originalRequest.newBuilder()
        .apply {
          if (token != null) {
            addHeader("Authorization", "Bearer $token")
          }
        }
        .build()

      chain.proceed(newRequest)
    }
  }

  @Provides
  @Singleton
  fun provideOkHttpClient(
    loggingInterceptor: HttpLoggingInterceptor,
    authInterceptor: Interceptor
  ): OkHttpClient {
    return OkHttpClient.Builder()
      .addInterceptor(authInterceptor)
      .addInterceptor(loggingInterceptor)
      .build()
  }

  @Provides
  @Singleton
  fun provideRetrofit(
    okHttpClient: OkHttpClient,
    json: Json
  ): Retrofit {
    val contentType = "application/json".toMediaType()
    return Retrofit.Builder()
      .baseUrl(API_BASE_URL)
      .client(okHttpClient)
      .addConverterFactory(json.asConverterFactory(contentType))
      .build()
  }

  @Provides
  @Singleton
  fun provideStudioApiService(retrofit: Retrofit): StudioApiService {
    return retrofit.create(StudioApiService::class.java)
  }

  @Provides
  @Singleton
  fun provideStudioApiClient(apiService: StudioApiService): StudioApiClient {
    return StudioApiClient(apiService)
  }
}
