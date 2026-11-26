package expo.modules.fastlinkspeedtest

import com.speedtest.sdk.SpeedTestClient
import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.bridge.SpeedTestEventMapper
import com.speedtest.sdk.bridge.SpeedTestSession
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FastlinkSpeedTestModule : Module() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val session = SpeedTestSession(scope) { sendEvent("onStateChange", it) }

  override fun definition() = ModuleDefinition {
    Name("FastlinkSpeedTest")
    Events("onStateChange")

    AsyncFunction("startTest") { config: Map<String, Any?>, promise: Promise ->
      val baseUrl = config["baseUrl"] as? String
      if (baseUrl.isNullOrBlank()) {
        promise.reject("INVALID_CONFIG", "baseUrl is required", null); return@AsyncFunction
      }
      fun long(k: String, d: Long) = (config[k] as? Number)?.toLong() ?: d
      val defaults = SpeedTestConfig(baseUrl = baseUrl)
      val cfg = SpeedTestConfig(
        baseUrl = baseUrl,
        username = config["username"] as? String,
        password = config["password"] as? String,
        pingTimeoutMs = long("pingTimeoutMs", defaults.pingTimeoutMs),
        downloadTimeoutMs = long("downloadTimeoutMs", defaults.downloadTimeoutMs),
        uploadTimeoutMs = long("uploadTimeoutMs", defaults.uploadTimeoutMs),
        overallTimeoutMs = long("overallTimeoutMs", defaults.overallTimeoutMs),
      )
      val interval = long("reportIntervalMs", 0)
      val c = SpeedTestClient(cfg)
      val runner = object : SpeedTestSession.Runner {
        override val state get() = c.state
        override suspend fun run() = c.runFullTest()
        override fun cancel() = c.cancel()
      }
      val started = session.start(
        runner, cfg.overallTimeoutMs, interval,
        onResult = { promise.resolve(it) },
        onError = { code, msg -> promise.reject(code, msg, null) },
      )
      if (!started) promise.reject(SpeedTestEventMapper.CODE_BUSY, "A speed test is already running", null)
    }

    Function("stopTest") { session.stop() }

    OnDestroy {
      session.stop() // settles a pending promise with CANCELLED
      scope.cancel()
    }
  }
}
