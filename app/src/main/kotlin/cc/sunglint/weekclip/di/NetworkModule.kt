package cc.sunglint.weekclip.di

import cc.sunglint.weekclip.BuildConfig
import cc.sunglint.weekclip.core.network.ApiEndpoints
import cc.sunglint.weekclip.core.network.AuthInterceptor
import cc.sunglint.weekclip.core.network.SessionAuthenticator
import cc.sunglint.weekclip.core.network.SessionCredentialProvider
import cc.sunglint.weekclip.core.session.AuthConfig
import cc.sunglint.weekclip.data.remote.WeekclipApiService
import cc.sunglint.weekclip.data.remote.auth.SupabaseAuthService
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
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The Supabase auth client, which is deliberately **not** the API client.
 *
 * If the refresh call went through the same `OkHttpClient`, it would carry
 * [AuthInterceptor] and [SessionAuthenticator] — so a refresh triggered by an
 * expired token would itself ask for a token, and a refresh that came back 401
 * would try to fix itself by refreshing. Both are the same bug: the mechanism
 * that renews the credential must not depend on the credential.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthApi

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
  fun provideOkHttpClient(credentials: SessionCredentialProvider): OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor(AuthInterceptor(credentials))
      // Renews on 401 and replays once. Registered as an authenticator rather
      // than a retry interceptor so OkHttp owns the loop guard.
      .authenticator(SessionAuthenticator(credentials))
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

  @Provides
  @Singleton
  @AuthApi
  fun provideAuthOkHttpClient(authConfig: AuthConfig): OkHttpClient =
    OkHttpClient.Builder()
      // GoTrue rejects a request with no project key before it looks at the
      // body, so this header is not optional even for an unauthenticated grant.
      .addInterceptor { chain ->
        chain.proceed(
          chain.request().newBuilder()
            .header("apikey", authConfig.anonKey)
            .build()
        )
      }
      .apply {
        if (BuildConfig.DEBUG) {
          // HEADERS, not BODY: the body of every call on this client is a
          // refresh token and its replacement. Printing those to logcat would
          // hand a persistent credential to anything that can read logs, and
          // unlike an access token it does not expire in an hour.
          addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }
          )
        }
      }
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()

  @Provides
  @Singleton
  @AuthApi
  fun provideAuthRetrofit(
    @AuthApi client: OkHttpClient,
    json: Json,
    authConfig: AuthConfig
  ): Retrofit = Retrofit.Builder()
    // A blank Supabase URL would throw from deep inside HttpUrl with a message
    // that names neither this app nor the missing build config field. Release
    // builds ship blank on purpose (AuthConfig), so this path is reachable, not
    // hypothetical — hence a placeholder host that the refresher never calls
    // because AuthConfig.isConfigured gates it first.
    .baseUrl(if (authConfig.isConfigured) authConfig.tokenBaseUrl else UNCONFIGURED_AUTH_BASE_URL)
    .client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

  @Provides
  @Singleton
  fun provideSupabaseAuthService(@AuthApi retrofit: Retrofit): SupabaseAuthService =
    retrofit.create(SupabaseAuthService::class.java)

  private const val UNCONFIGURED_AUTH_BASE_URL = "https://auth.invalid/auth/v1/"
}
