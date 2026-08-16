package cc.sunglint.weekclip

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Nothing runs at process start.
 *
 * It used to: an `AppInitializer` multibinding existed so the debug source set
 * could sign in before any screen appeared (148.5c). That was the right shape
 * while there was no login screen to reach — and the wrong one the moment there
 * was, because it walked straight past the gate. The debug sign-in is now a
 * button on that gate (`ui/auth/DebugSignInAction`), and with its only
 * contributor gone the extension point had none left.
 *
 * Work that must outlive a screen belongs in the component that owns it —
 * `SessionManager` is a `@Singleton` and reads its store on first use. Starting
 * things here instead trades a lazily-correct order for a hand-maintained one.
 */
@HiltAndroidApp
class WeekclipApplication : Application()
