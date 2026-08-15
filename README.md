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

## UI verification (Maestro)

Unit tests never construct the Activity, so until now nothing here could tell
you the app actually launches. `maestro/` fixes that — flows that drive the
installed app on a **USB-attached device** and report back machine-readably.

```bash
./scripts/run-maestro.sh          # build + install + run every flow
```

Needs the [Maestro](https://maestro.dev) CLI:
`curl -fsSL "https://get.maestro.mobile.dev" | bash`. Results go to
`build/maestro/`: JUnit XML, and for each failing step a screenshot, that
step's view hierarchy as JSON, and the device logcat.

Emulators do not work on the current dev Mac — `maestro/README.md` records the
measurement and why a real device is the better answer anyway.

**For agents:** `.mcp.json` registers Maestro's MCP server (it ships inside the
CLI). That exposes `inspect_screen` (compact view hierarchy),
`take_screenshot`, and `run` (inline flow YAML) — enough to look at the screen,
act on it, and check the result without writing a flow file first. Prefer
`inspect_screen` over `maestro hierarchy`: the raw dump on a Samsung device is
~59KB of `systemui` and `sidegesturepad` chrome.

CI runs `maestro check-syntax` on every flow. It cannot run the flows
themselves — that needs hardware.

## Layout

Three layers, dependencies pointing inward: `ui` → `domain` ← `data`. The UI
never names a Retrofit or serialization type; `domain` has no `android.*` import
at all.

```
app/src/main/kotlin/cc/sunglint/weekclip/
├── WeekclipApplication.kt      # @HiltAndroidApp
├── MainActivity.kt             # single activity, Compose host
├── core/
│   ├── network/                # base URLs (from BuildConfig), envelope,
│   │                           # auth interceptor, session-token seam
│   └── result/                 # AppError + AppResult — the closed failure set
├── domain/                     # pure Kotlin: models, repository interfaces, use cases
├── data/
│   ├── remote/                 # Retrofit service, DTOs, ApiCall (error mapping)
│   └── repository/             # implementations of the domain interfaces
├── di/                         # Hilt modules (network, repositories, dispatchers)
└── ui/
    ├── dashboard/              # UiState + ViewModel + stateless screen
    ├── navigation/             # route table + NavHost
    └── theme/                  # placeholder palette until Phase 5 wires the DS
```

### Base URLs come from the build type

`buildConfigField` in `app/build.gradle.kts` sets `API_BASE_URL` /
`USER_API_BASE_URL` per build type — debug points at `*.weekclip.dev`, release at
`*.weekclip.com`. These mirror the `routes` in each service's `wrangler.jsonc`.
The two tiers are **separate Cloudflare accounts with separate databases**, so a
constant shared between them would be a way to write debug traffic into
production. `ApiEndpoints` rejects a blank URL loudly rather than letting
Retrofit fail from inside `HttpUrl`.

### Errors are a closed set

`core/result/AppError` is what the UI is allowed to see; `data/remote/ApiCall`
is the only place transport exceptions become one. That means the `when` in
`DashboardScreen` is exhaustive, and adding an error case breaks the build at
every screen that has to decide what to say about it — which is the point.

`AppError` carries no user-facing copy. Strings live in `strings.xml` so they
are translatable and so the payment-string gate has one file to read.

### The dashboard is the reference screen

`ui/dashboard/` is the shape every Phase 5 screen should copy: one immutable
`UiState`, a read-only `StateFlow` (Kotlin explicit backing field), a stateless
`@Composable` that takes values and emits events, and a thin `Route` wrapper
that is the only thing which knows a ViewModel exists.

It is also the vertical slice that proves the spine is connected: it runs a real
`GET /studios` against the contract in weekclip-api. `StudioRepositoryContractTest`
pins that contract with MockWebServer — real JSON over a real socket, because a
fake of the API interface would skip the envelope, which is the part most likely
to be wrong.

### The route table is a contract

`ui/navigation/WeekclipRoutes.kt` mirrors weekclip-web's URLs one-for-one. That
is load-bearing, not cosmetic: PRD-0008 D4 routes `/studios/:id/media/:mid`,
`/invite/:token` and `/share/:token` into the app via App Links, and PRD-0007 D6
named the media route as "the boundary where native push/pop attaches". If these
drift from the web paths, deep links stop resolving. `WeekclipRoutesTest` asserts
each builder against the pattern it fills.

## Status

Spine, not features. The dashboard is real and reaches the live API; every other
destination is still a placeholder. Feature work is Phase 5 of PRD-0008.

Not built yet, on purpose:

| Missing | Why it is not here |
|---|---|
| Local cache (Room) | PRD-0008 states no offline requirement. A schema with no read path is a migration liability from day one; the repository interface is the seam that makes it addable |
| Auth / session storage | Task 148.5. `SessionTokenProvider` is the seam — today it returns null and requests come back 401, which the UI already renders |
| One-off event channel (`SharedFlow`) | There is no event to send yet. An empty channel plus an unread `LaunchedEffect` is worse than nothing |
| App Links intent filters | Task 148.5, and they need `assetlinks.json` served from `weekclip.com` first. Declaring them before the domain verifies gives users a chooser dialog |
