package cc.sunglint.weekclip

import android.app.Application
import cc.sunglint.weekclip.di.AppInitializer
import cc.sunglint.weekclip.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class WeekclipApplication : Application() {

  /**
   * Empty in a release build — the set has no contributors outside the debug
   * source set today. Injecting a set rather than naming initializers here
   * keeps `main` free of any reference to debug-only code.
   */
  @Inject
  lateinit var initializers: Set<@JvmSuppressWildcards AppInitializer>

  @Inject
  @ApplicationScope
  lateinit var applicationScope: CoroutineScope

  override fun onCreate() {
    super.onCreate()
    initializers.forEach { it.initialize(applicationScope) }
  }
}
