plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)
  id("kotlin-kapt")
}

android {
  namespace = "com.weekclip.android"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.weekclip.android"
    minSdk = 28
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {
      isMinifyEnabled = false
      isDebuggable = true
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
  }

  lint {
    quiet = false
    abortOnError = true
    disable += "MissingTranslation"
  }
}

dependencies {
  // Kotlin
  implementation(libs.kotlin.stdlib)

  // Android Core
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.work.runtime.ktx)

  // Jetpack Compose
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.runtime)
  debugImplementation(libs.compose.ui.tooling)
  debugImplementation(libs.compose.ui.test.manifest)

  // Material Design
  implementation(libs.material3)

  // Networking
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.retrofit)
  implementation(libs.retrofit.kotlinx.serialization)

  // Serialization
  implementation(libs.kotlinx.serialization.json)

  // Dependency Injection
  implementation(libs.hilt.android)
  kapt(libs.hilt.android.compiler)
  implementation(libs.hilt.work)
  kapt("com.google.dagger:hilt-compiler:${libs.versions.hilt.get()}")

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Logging
  implementation(libs.timber)

  // Authentication
  implementation(libs.supabase.kotlin)
  implementation(libs.supabase.auth)
  implementation(libs.google.auth.client)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)

  // Testing
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.espresso)
  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.compose.ui.test.junit4)
}

kapt {
  correctErrorTypes = true
}
