# Fastlink SpeedTest — Usage Guide

Fastlink SpeedTest ships as two artifacts under one brand. **Pick one** based on your needs:

| Artifact | When to pick it |
|---|---|
| **`fastlink-speedtest-core`** | You want the headless engine and will build your own UI. |
| **`fastlink-speedtest-ui`**   | You want the drop-in Jetpack Compose screen. Automatically pulls `core` transitively. |

You never need both — `ui` already depends on `core`.

---

## 1. Add the dependency

Once published (see `PUBLISHING.md`), add **one** of these to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    // Option A — UI included (recommended for quick integration)
    implementation("io.github.bilalseerwan:fastlink-speedtest-ui:1.0.0")

    // Option B — Engine only (build your own UI)
    implementation("io.github.bilalseerwan:fastlink-speedtest-core:1.0.0")
}
```

> If publishing through **JitPack** instead of Maven Central, the coordinates become
> `com.github.bilalseerwan.speedtest-sdk:fastlink-speedtest-ui:1.0.0` (or `:fastlink-speedtest-core:1.0.0`).

Requirements:
- `minSdk` 24+
- Kotlin 1.9+, JDK 17
- Internet permission in your app manifest:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

---

## 2. Using `speedtest-core` (headless)

```kotlin
import com.speedtest.sdk.SpeedTestClient
import com.speedtest.sdk.SpeedTestConfig
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val client = SpeedTestClient(
        SpeedTestConfig(
            baseUrl = "https://speed.example.com",
            // optional:
            username = "user",
            password = "pass",
            downloadTimeoutMs = 30_000,
            uploadTimeoutMs   = 30_000,
        )
    )

    init {
        // Observe live state for progress UI
        viewModelScope.launch {
            client.state.collect { state ->
                // state is a sealed SpeedTestState (Idle / Pinging / Downloading / ...)
            }
        }
    }

    fun start() = viewModelScope.launch {
        val result = client.runFullTest()
        // result.ping.avg, result.download.finalMbps, result.upload.finalMbps
    }

    fun stop() = client.cancel()
}
```

Run a single phase only:

```kotlin
val ping     = client.runPingOnly()                       // PingStats
val download = client.runDownloadOnly()                   // PhaseResult
val upload   = client.runUploadOnly(probeMbps = 50.0)     // PhaseResult
```

---

## 3. Using `speedtest-ui` (drop-in Compose screen)

`speedtest-ui` uses **Hilt**, so your app must be Hilt-enabled (`@HiltAndroidApp` Application + `@AndroidEntryPoint` Activity).

```kotlin
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.ui.SpeedTestScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpeedTestScreen(
                config = SpeedTestConfig(baseUrl = "https://speed.example.com"),
                onFinished = { result ->
                    Log.d("SpeedTest", "DL=${result.download.finalMbps} Mbps")
                }
            )
        }
    }
}
```

That's it — the composable handles state collection, the gauge animation, ping display, and the final summary.

---

## 4. Configuration reference

| Field | Default | Description |
|---|---|---|
| `baseUrl` | required | Speed-test server base URL |
| `username` / `password` | `null` | Optional HTTP Basic Auth |
| `pingTimeoutMs` | `5_000` | Ping phase timeout |
| `downloadTimeoutMs` | `30_000` | Download phase timeout |
| `uploadTimeoutMs` | `30_000` | Upload phase timeout |
| `requestTimeoutMs` | `30_000` | Per-request OkHttp timeout |
| `overallTimeoutMs` | `120_000` | Hard ceiling for the entire test |

---

## 5. Server contract

The SDK expects a server exposing the standard speed-test endpoints used by `SpeedTestApiClient` (`/health`, `/files`, `/download/{file}`, `/upload`). Point `baseUrl` at any compatible deployment.

---

## 6. Troubleshooting

- **`UnsatisfiedLinkError` for Hilt**: ensure your app applies `dagger.hilt.android.plugin` and `ksp`.
- **Compose version mismatch**: `speedtest-ui` is built against Compose Compiler `1.5.8`. Use Kotlin `1.9.22+`.
- **Test never starts**: confirm `INTERNET` permission and that `baseUrl` is reachable from the device.
