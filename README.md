# WeekClip Android

Modern Android native app for WeekClip with Jetpack Compose and MVVM architecture.

## Architecture

### Modern Stack (2024-2026)
- **UI Framework**: Jetpack Compose (declarative UI)
- **Architecture Pattern**: MVVM + Clean Architecture (3-layer)
- **State Management**: StateFlow + Kotlin coroutines
- **Dependency Injection**: Hilt
- **Networking**: Retrofit + OkHttp + kotlinx-serialization
- **Background Tasks**: WorkManager (for scheduled uploads)
- **Async**: Kotlin Coroutines + async/await

### Project Structure
```
app/
├── src/main/
│   ├── kotlin/com/weekclip/android/
│   │   ├── data/              # Data layer (repositories, API clients)
│   │   ├── domain/            # Domain layer (use cases, business logic)
│   │   ├── presentation/      # Presentation layer (ViewModels, Composables)
│   │   ├── di/               # Dependency injection modules
│   │   └── ui/               # UI components and themes
│   └── res/                  # Android resources
└── build.gradle.kts          # Build configuration
```

## Setup

### Prerequisites
- Android Studio Jellyfish (2023.3.1) or newer
- JDK 17+
- Android SDK 35+
- Gradle 8.4+

### Building
```bash
./gradlew build
./gradlew test
```

### Running
```bash
./gradlew installDebug
```

## Development

### Code Style
- Kotlin with consistent formatting (ktlint compatible)
- Sealed classes for sealed types
- Data classes for models
- Extension functions for utility

### Testing
- Unit tests with MockK
- Integration tests with Hilt
- UI tests with Compose test framework

## Key Features

### Current
- [x] Modern Compose UI framework
- [x] Dependency injection with Hilt
- [x] Network layer with Retrofit
- [x] MVVM architecture

### Planned
- [ ] Video playback
- [ ] Background upload with WorkManager
- [ ] Scheduled WiFi upload
- [ ] Media gallery integration
- [ ] User authentication

## References
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Hilt Dependency Injection](https://dagger.dev/hilt/)
- [WorkManager Guide](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
