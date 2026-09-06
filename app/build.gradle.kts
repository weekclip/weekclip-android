plugins {
  alias(libs.plugins.android.application)
  // NOTE: no `org.jetbrains.kotlin.android` here — AGP 9.0+ has built-in Kotlin
  // support and hard-errors if that plugin is also applied.
  // https://kotl.in/gradle/agp-built-in-kotlin
  //
  // The Compose compiler is its own plugin since Kotlin 2.0; the old
  // `composeOptions.kotlinCompilerExtensionVersion` knob the previous skeleton
  // used has been inert since then (#148).
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
  namespace = "cc.sunglint.weekclip"
  compileSdk = libs.versions.compile.sdk.get().toInt()

  defaultConfig {
    applicationId = "cc.sunglint.weekclip"
    minSdk = libs.versions.min.sdk.get().toInt()
    targetSdk = libs.versions.target.sdk.get().toInt()
    versionCode = 1
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // ---------------------------------------------------------------------------
  // Build-time values that must not be committed
  //
  // The Supabase anon key is *public* by construction — weekclip.com serves it
  // to every browser — but the repo is not where it lives. `secrets/*.enc.yaml`
  // in the superrepo is the value ledger, and copying a value out of a ledger
  // into a tracked file is how ledgers start lying. So these arrive from the
  // environment, and default to empty so a clean checkout and CI both build:
  //
  //   WEEKCLIP_SUPABASE_ANON_KEY_DEV   vault.sh get dev SUPABASE_ANON_KEY
  //   WEEKCLIP_DEBUG_SIGN_IN_EMAIL     a dev-tier account, e.g. adam@weekclip.com
  //   WEEKCLIP_DEBUG_SIGN_IN_PASSWORD  vault.sh get dev CAPTURE_BOT_PASSWORD
  //
  // What an empty value costs is stated where it is read — `AuthConfig`
  // for the key, `DebugAutoSignIn` for the credentials.
  //
  // Read inline rather than hoisted into a `val`, which is not a style choice:
  //   · a `val` INSIDE this `android { }` block is a **parse error** on AGP
  //     9.3.1 / Gradle 9.7 — `Expecting '}'`, pointing at the block's brace
  //     rather than at the declaration;
  //   · a `val` at script top level compiles, and then the whole script stops
  //     configuring the project — AGP reports "does not specify `compileSdk`"
  //     for a file that plainly does, and Hilt reports its own dependency
  //     missing. Both measured while writing this.
  // ---------------------------------------------------------------------------
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

      // Production hosts. These mirror weekclip-web's defaults in
      // `src/shared/api/client.ts` (getApiBaseUrl / getUserApiBaseUrl) — the
      // app talks to the same two services the browser does.
      buildConfigField("String", "API_BASE_URL", "\"https://service-api.weekclip.com/api/v1\"")
      buildConfigField("String", "USER_API_BASE_URL", "\"https://user-api.weekclip.com/api/v1\"")

      // Production Supabase. The URL is already committed in every service's
      // wrangler.jsonc — it is an address, not a credential.
      buildConfigField("String", "SUPABASE_URL", "\"https://pmkuddfuwdbvsjwudgii.supabase.co\"")
      buildConfigField("String", "SUPABASE_ANON_KEY", "\"${System.getenv("WEEKCLIP_SUPABASE_ANON_KEY_PROD") ?: ""}\"")
    }
    debug {
      isMinifyEnabled = false

      // The dev tier. Note this is `.dev`, not `.com`: the two are different
      // Cloudflare accounts with different databases (see the superrepo's
      // secrets tier table). A debug build must never reach production data.
      buildConfigField("String", "API_BASE_URL", "\"https://service-api.weekclip.dev/api/v1\"")
      buildConfigField("String", "USER_API_BASE_URL", "\"https://user-api.weekclip.dev/api/v1\"")

      // The dev Supabase project — a different project with different users, so
      // a debug build cannot mint a token production would accept even by
      // accident.
      buildConfigField("String", "SUPABASE_URL", "\"https://cgyrzvgrjxhreinxkjic.supabase.co\"")
      buildConfigField("String", "SUPABASE_ANON_KEY", "\"${System.getenv("WEEKCLIP_SUPABASE_ANON_KEY_DEV") ?: ""}\"")

      // Debug-only sign-in (PRD-0008 148.5). The product's only login is Google
      // OAuth, which needs per-platform OAuth clients that do not exist yet
      // (148.5c-b) — so without this there is no way to put a real token in
      // front of the session code on a real device, and "the store works" would
      // rest entirely on unit tests. These two fields exist only in the debug
      // build type, and the code that reads them only in the debug source set.
      buildConfigField("String", "DEBUG_SIGN_IN_EMAIL", "\"${System.getenv("WEEKCLIP_DEBUG_SIGN_IN_EMAIL") ?: ""}\"")
      buildConfigField("String", "DEBUG_SIGN_IN_PASSWORD", "\"${System.getenv("WEEKCLIP_DEBUG_SIGN_IN_PASSWORD") ?: ""}\"")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  buildFeatures {
    compose = true
    // Off by default since AGP 8. The base URLs above are the reason it is on:
    // they must differ per build type, which rules out a Kotlin constant.
    buildConfig = true
  }

  lint {
    abortOnError = true
    warningsAsErrors = false
    disable += "MissingTranslation"
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.datastore.preferences)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  debugImplementation(libs.compose.ui.tooling)
  debugImplementation(libs.compose.ui.test.manifest)

  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  // Playback (ADR-0002 D5 / PRD-0007 1층: 360p HLS)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.ui.compose)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  implementation(libs.retrofit)
  implementation(libs.retrofit.kotlinx.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.okhttp.mockwebserver)

  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.compose.ui.test.junit4)
}
