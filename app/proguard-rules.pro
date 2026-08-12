# Android default ProGuard rules
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View

# Hilt
-keep class dagger.hilt.internal.aggregated** { *; }
-keep class hilt_aggregated_deps { *; }

# Kotlin
-keep class kotlin.** { *; }
-keepclassmembers class ** {
    ** INSTANCE;
}

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <methods>;
}

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
