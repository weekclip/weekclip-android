package cc.sunglint.weekclip.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import cc.sunglint.weekclip.core.network.SessionCredentialProvider
import cc.sunglint.weekclip.core.network.SessionManagerCredentialProvider
import cc.sunglint.weekclip.core.session.AndroidKeystoreCipher
import cc.sunglint.weekclip.core.session.AuthConfig
import cc.sunglint.weekclip.core.session.DataStoreSessionStore
import cc.sunglint.weekclip.core.session.SecretCipher
import cc.sunglint.weekclip.core.session.SessionClock
import cc.sunglint.weekclip.core.session.SessionRefresher
import cc.sunglint.weekclip.core.session.SessionStore
import cc.sunglint.weekclip.data.repository.SupabaseSessionRefresher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * One DataStore file for the session, separate from any future preferences
 * store. Mixing an encrypted credential blob into the file that also holds
 * "dark mode: on" means every unrelated preference write rewrites the
 * credential, and every corruption of one loses the other.
 */
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

  @Provides
  @Singleton
  fun provideAuthConfig(): AuthConfig = AuthConfig.fromBuildConfig()

  @Provides
  @Singleton
  fun provideSessionClock(): SessionClock = SessionClock.System

  @Provides
  @Singleton
  fun provideSessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    context.sessionDataStore

  @Provides
  @Singleton
  fun provideSecretCipher(): SecretCipher = AndroidKeystoreCipher()

  @Provides
  @Singleton
  fun provideSessionStore(
    dataStore: DataStore<Preferences>,
    cipher: SecretCipher,
    json: Json
  ): SessionStore = DataStoreSessionStore(dataStore, cipher, json)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionBindings {

  @Binds
  @Singleton
  abstract fun bindSessionRefresher(impl: SupabaseSessionRefresher): SessionRefresher

  @Binds
  @Singleton
  abstract fun bindSessionCredentialProvider(
    impl: SessionManagerCredentialProvider
  ): SessionCredentialProvider
}
