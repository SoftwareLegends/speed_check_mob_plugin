# Fastlink SpeedTest — Project Structure

This document describes the full structure of the Fastlink SpeedTest project.

## Top-Level Layout

```
speedtest-sdk/
├── build.gradle.kts              # Root Gradle build (plugin aliases only)
├── settings.gradle.kts           # Module declarations + repositories
├── gradle.properties             # JVM args / AndroidX flags
├── gradle/
│   └── libs.versions.toml        # Centralized dependency catalog
├── fastlink-speedtest-core/               # Pure Kotlin/Android library — engine + models
├── fastlink-speedtest-ui/                 # Jetpack Compose UI + ViewModel (depends on core)
├── sample/                       # Demo app showing UI integration
└── sample-core/                  # Demo app showing core-only integration
```

## Modules

### `:fastlink-speedtest-core` (Android Library)

Headless engine. No UI dependencies. Namespace: `com.speedtest.sdk.core`.

```
fastlink-speedtest-core/
├── build.gradle.kts              # android-library + kotlin + serialization
├── consumer-rules.pro            # ProGuard rules pulled in by consumers
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   └── kotlin/com/speedtest/sdk/
    │       ├── SpeedTestClient.kt      # PUBLIC entry point
    │       ├── SpeedTestConfig.kt      # PUBLIC configuration data class
    │       ├── SpeedTestState.kt       # PUBLIC sealed state for UI observers
    │       ├── SpeedTestResult.kt      # PUBLIC result model
    │       ├── engine/
    │       │   ├── TestOrchestrator.kt # Coordinates phases
    │       │   ├── PingEngine.kt
    │       │   ├── ProbeEngine.kt
    │       │   ├── DownloadEngine.kt
    │       │   ├── DownloadWorker.kt
    │       │   ├── UploadEngine.kt
    │       │   └── UploadWorker.kt
    │       ├── net/
    │       │   ├── SpeedTestApiClient.kt   # OkHttp client wrapper
    │       │   ├── HealthApi.kt
    │       │   ├── FilesApi.kt
    │       │   ├── DownloadApi.kt
    │       │   ├── UploadApi.kt
    │       │   └── CountingRequestBody.kt  # Byte-counting upload body
    │       ├── math/
    │       │   ├── PingCalculator.kt        # avg / jitter
    │       │   ├── SpeedSampler.kt
    │       │   └── SustainedScorer.kt       # Steady-state speed picker
    │       ├── model/
    │       │   ├── PingStats.kt
    │       │   ├── PhaseResult.kt
    │       │   ├── SpeedSample.kt
    │       │   ├── TestFile.kt
    │       │   ├── Tier.kt
    │       │   └── TierSelector.kt          # Probe-based size tier
    │       └── util/
    │           ├── EwmaFilter.kt            # Exponential moving average
    │           ├── PayloadCache.kt          # Reused upload payload buffer
    │           └── SdkLogger.kt
    └── test/kotlin/com/speedtest/sdk/       # JUnit unit tests
        ├── PingCalculatorTest.kt
        ├── SustainedScorerTest.kt
        ├── TierSelectorTest.kt
        ├── SpeedTestApiClientTest.kt
        └── CountingRequestBodyTest.kt
```

**Dependencies:** kotlinx-coroutines, kotlinx-serialization-json, okhttp + logging interceptor.

### `:fastlink-speedtest-ui` (Android Library)

Optional drop-in Compose UI. Namespace: `com.speedtest.sdk.ui`. Depends on `:fastlink-speedtest-core` via `api`, so consumers of `fastlink-speedtest-ui` automatically get `fastlink-speedtest-core`.

```
fastlink-speedtest-ui/
├── build.gradle.kts              # android-library + compose + hilt + ksp
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/com/speedtest/sdk/ui/
        ├── SpeedTestScreen.kt           # PUBLIC top-level composable
        ├── SpeedTestViewModel.kt        # @HiltViewModel
        ├── theme/
        │   └── SpeedTestTheme.kt
        └── components/
            ├── SpeedGauge.kt
            ├── PhaseIndicator.kt
            ├── PingDisplay.kt
            └── ResultSummary.kt
```

**Dependencies:** Compose BOM, Material3, Lifecycle Compose, Hilt + Hilt Navigation Compose.

### `:sample` and `:sample-core`

Reference apps. Not published. `sample` consumes `:fastlink-speedtest-ui`, `sample-core` consumes `:fastlink-speedtest-core` directly.

## Public API Surface

| Symbol | Module | Purpose |
|---|---|---|
| `SpeedTestClient(config)` | core | Main entry; `runFullTest()`, `runPingOnly()`, `runDownloadOnly()`, `runUploadOnly()`, `cancel()` |
| `SpeedTestConfig` | core | `baseUrl`, optional basic-auth, per-phase timeouts |
| `SpeedTestState` | core | Sealed flow state (`Idle`, `Connecting`, `Pinging`, `Downloading`, `Uploading`, `Done`, …) |
| `SpeedTestResult` | core | Final aggregate result |
| `PingStats`, `PhaseResult` | core | Per-phase result models |
| `SpeedTestScreen(config, onFinished)` | ui | Drop-in full-screen composable |

## Test Phases (Engine Pipeline)

`prewarm → ping → probe → tier-select → download → upload`

Orchestrated by `TestOrchestrator`, with cooperative cancellation via coroutines and a global `overallTimeoutMs` from `SpeedTestConfig`.
