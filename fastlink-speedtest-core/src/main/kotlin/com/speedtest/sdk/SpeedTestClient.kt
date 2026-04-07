package com.speedtest.sdk

import com.speedtest.sdk.engine.TestOrchestrator
import com.speedtest.sdk.model.PhaseResult
import com.speedtest.sdk.model.PingStats
import com.speedtest.sdk.model.TierSelector
import com.speedtest.sdk.util.PayloadCache
import com.speedtest.sdk.util.SdkLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main entry point for the SpeedTest SDK.
 *
 * Usage:
 * ```kotlin
 * val client = SpeedTestClient(SpeedTestConfig(baseUrl = "https://speed.example.com"))
 * // Observe state for real-time UI updates
 * client.state.collect { state -> updateUI(state) }
 * // Run test
 * val result = client.runFullTest()
 * ```
 *
 * @param config Configuration for the speed test
 */
class SpeedTestClient(private val config: SpeedTestConfig) {

    private val _state = MutableStateFlow<SpeedTestState>(SpeedTestState.Idle)

    /** Observe for real-time state updates during the test. */
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    private val orchestrator = TestOrchestrator(config, _state)
    private var testJob: Job? = null

    /**
     * Run a full speed test: prewarm -> ping -> probe -> download -> upload.
     * @return The complete test result
     * @throws CancellationException if cancelled via [cancel]
     */
    suspend fun runFullTest(): SpeedTestResult {
        SdkLogger.d(TAG, "runFullTest() called — baseUrl=${config.baseUrl}")
        _state.value = SpeedTestState.Idle
        return coroutineScope {
            val job = coroutineContext.job
            testJob = job
            try {
                val result = orchestrator.runFullTest()
                SdkLogger.d(TAG, "runFullTest() complete — DL=${result.download.finalMbps} Mbps, UL=${result.upload.finalMbps} Mbps, Ping=${result.ping.avg} ms")
                result
            } finally {
                PayloadCache.clear()
                testJob = null
            }
        }
    }

    /**
     * Run only the ping measurement phase.
     */
    suspend fun runPingOnly(): PingStats {
        SdkLogger.d(TAG, "runPingOnly() called")
        _state.value = SpeedTestState.Idle
        return coroutineScope {
            testJob = coroutineContext.job
            try {
                _state.value = SpeedTestState.Connecting
                val result = orchestrator.runPing()
                SdkLogger.d(TAG, "runPingOnly() complete — avg=${result.avg} ms, jitter=${result.jitter} ms")
                result
            } finally {
                testJob = null
            }
        }
    }

    /**
     * Run only the download measurement phase.
     * @param probeMbps Optional pre-determined probe speed. If null, a probe will be run.
     */
    suspend fun runDownloadOnly(probeMbps: Double? = null): PhaseResult {
        _state.value = SpeedTestState.Idle
        return coroutineScope {
            testJob = coroutineContext.job
            try {
                _state.value = SpeedTestState.Connecting
                val probeResult = probeMbps ?: orchestrator.runProbe()
                val tier = TierSelector.pickTier(probeResult)
                orchestrator.runDownload(tier, config.downloadTimeoutMs)
            } finally {
                testJob = null
            }
        }
    }

    /**
     * Run only the upload measurement phase.
     * @param probeMbps Optional pre-determined probe speed. If null, a probe will be run.
     */
    suspend fun runUploadOnly(probeMbps: Double? = null): PhaseResult {
        _state.value = SpeedTestState.Idle
        return coroutineScope {
            testJob = coroutineContext.job
            try {
                _state.value = SpeedTestState.Connecting
                val probeResult = probeMbps ?: orchestrator.runProbe()
                val tier = TierSelector.pickTier(probeResult * 0.6)
                orchestrator.runUpload(tier, config.uploadTimeoutMs)
            } finally {
                PayloadCache.clear()
                testJob = null
            }
        }
    }

    /**
     * Cancel a running test.
     */
    fun cancel() {
        SdkLogger.d(TAG, "cancel() called")
        testJob?.cancel()
        _state.value = SpeedTestState.Idle
    }

    companion object {
        private const val TAG = "Client"
    }
}
