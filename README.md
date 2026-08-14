# weekclip-android

Native Android client for weekclip.

- **PRD**: `weekclip-harness/wiki/prd/PRD-0008-native-app-port.md`
- **Stack decisions**: `weekclip-harness/wiki/adr/ADR-0002-native-app-stack.md`
- **Port scope**: `docs/product/native-app-feature-inventory.md` (superrepo)

## Stack

Every choice below has a recorded reason in ADR-0002. Where a decision looks
unusual, the reason is in the third column — not in someone's memory.

| Area | Choice | Why |
|------|--------|-----|
| UI | Jetpack Compose | Default for new Android apps |
| State | ViewModel + StateFlow | — |
| Navigation | **Navigation 2** (`navigation-compose`) | Navigation 3 is still alpha (`1.2.0-alpha02`, 2026-08). ADR-0002 D2 |
| DI | Hilt (via KSP) | Compile-time verification |
| Networking | Retrofit + OkHttp | ADR-0002 D6 |
| Playback | **Media3 / ExoPlayer** | The platform `MediaPlayer`'s HLS support is not production-usable, and 360p HLS is the whole browsing tier (PRD-0007). ADR-0002 D5 |
| Background upload | WorkManager + Foreground Service | Phase 5. Survives backgrounding, not task-kill (PRD-0008 D8) |
| Secure storage | EncryptedSharedPreferences / DataStore | — |

**No payment code, ever.** PRD-0008 D3 requires zero payment-related strings in
the app binary; a CI gate enforces it (N6). Capacity shortfall is reported as a
plain fact — no price, no top-up path, no "buy on the web".

## Requirements

- JDK 17+ (verified on Corretto 21)
- Android SDK platform **37** (Android 17) + build-tools
- No local Gradle install needed — **the wrapper is committed**

> The wrapper's absence is why this repo's CI failed at `chmod +x gradlew` for
> its entire history. Do not gitignore `gradlew` or `gradle/wrapper/`.

## Build

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew lintDebug             # lint (abortOnError = true)
./gradlew assembleDebug         # debug APK
./gradlew assembleRelease       # release APK (R8 minify + resource shrink)
```

`local.properties` (gitignored) needs `sdk.dir=$HOME/Library/Android/sdk`, or set
`ANDROID_HOME`.

## Layout

```
app/src/main/kotlin/cc/sunglint/weekclip/
├── WeekclipApplication.kt      # @HiltAndroidApp
├── MainActivity.kt             # single activity, Compose host
├── core/network/               # service base URLs (billing deliberately absent)
├── di/                         # Hilt modules
└── ui/
    ├── navigation/             # route table + NavHost
    └── theme/                  # placeholder palette until Phase 5 wires the DS
```

### The route table is a contract

`ui/navigation/WeekclipRoutes.kt` mirrors weekclip-web's URLs one-for-one. That
is load-bearing, not cosmetic: PRD-0008 D4 routes `/studios/:id/media/:mid`,
`/invite/:token` and `/share/:token` into the app via App Links, and PRD-0007 D6
named the media route as "the boundary where native push/pop attaches". If these
drift from the web paths, deep links stop resolving. `WeekclipRoutesTest` asserts
each builder against the pattern it fills.

## Status

Skeleton only — every screen is a placeholder. Feature work is Phase 5 of PRD-0008.
