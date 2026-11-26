package expo.modules.fastlinkspeedtest

import com.speedtest.sdk.SpeedTestClient
import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.SpeedTestState
import com.speedtest.sdk.bridge.SpeedTestEventMapper
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class FastlinkSpeedTestModule : Module() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var client: SpeedTestClient? = null
  private var job: Job? = null
  @Volatile private var cancelled = false

  override fun definition() = ModuleDefinition {
    Name("FastlinkSpeedTest")
    Events("onStateChange")

    AsyncFunction("startTest") { config: Map<String, Any?>, promise: Promise ->
      if (job?.isActive == true) {
        promise.reject("BUSY", "A speed test is already running", null); return@AsyncFunction
      }
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
      client = c
      cancelled = false
      job = scope.launch {
        val collector = launch {
          var last = 0L
          c.state.collect { s ->
            val terminal = s is SpeedTestState.Finished || s is SpeedTestState.Error
            val now = System.currentTimeMillis()
            if (terminal || interval <= 0 || now - last >= interval) {
              last = now
              sendEvent("onStateChange", SpeedTestEventMapper.toEvent(s))
            }
          }
        }
        try {
          val r = withTimeout(cfg.overallTimeoutMs) { c.runFullTest() }
          promise.resolve(SpeedTestEventMapper.toResult(r))
        } catch (e: TimeoutCancellationException) {
          sendEvent("onStateChange", SpeedTestEventMapper.errorEvent("overall", e))
          promise.reject(SpeedTestEventMapper.CODE_TIMEOUT, "Speed test timed out", e)
        } catch (e: CancellationException) {
          if (cancelled) {
            sendEvent("onStateChange", SpeedTestEventMapper.cancelledEvent())
            promise.reject(SpeedTestEventMapper.CODE_CANCELLED, "Speed test cancelled", null)
          } else throw e
        } catch (e: Throwable) {
          sendEvent("onStateChange", SpeedTestEventMapper.errorEvent("test", e))
          promise.reject(SpeedTestEventMapper.codeFor(e), SpeedTestEventMapper.sanitize(e.message ?: "error"), e)
        } finally {
          collector.cancel()
          client = null
        }
      }
    }

    Function("stopTest") {
      cancelled = true
      client?.cancel()
      job?.cancel()
    }

    OnDestroy { scope.cancel() }
  }
}
