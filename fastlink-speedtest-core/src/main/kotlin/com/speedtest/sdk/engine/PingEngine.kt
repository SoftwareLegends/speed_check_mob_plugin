package com.speedtest.sdk.engine

import com.speedtest.sdk.math.PingCalculator
import com.speedtest.sdk.model.PingStats
import com.speedtest.sdk.net.HealthApi
import com.speedtest.sdk.util.SdkLogger
import com.speedtest.sdk.SpeedTestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Measures latency to the speed test server.
 *
 * Algorithm:
 * 1. Pre-warm with 5 HEAD requests (discarded)
 * 2. Sample GET /health RTTs
 * 3. Early-stop when stable for 2.5s with >= 12 samples
 * 4. Hard-stop at 5s
 */
class PingEngine(
    private val healthApi: HealthApi,
    private val maxMs: Long = 5000,
    private val stableHoldMs: Long = 2500,
    private val minSamples: Int = 12,
    private val onSample: ((List<Int>, Int) -> Unit)? = null,
) {

    suspend fun run(): PingStats = withContext(Dispatchers.IO) {
        // 1. Warmup: 5 sequential GET /health, discard results
        SdkLogger.d(TAG, "Starting warmup — 5 GET /health requests")
        repeat(5) { i ->
            ensureActive()
            try {
                val rtt = healthApi.pingHealth()
                SdkLogger.d(TAG, "Warmup ${i + 1}/5 — RTT=${rtt} ms")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                SdkLogger.w(TAG, "Warmup ${i + 1}/5 failed: ${e.message}")
            }
        }
        SdkLogger.d(TAG, "Warmup complete, starting sample loop (maxMs=$maxMs, stableHoldMs=$stableHoldMs)")

        // 2. Sample loop
        val samples = mutableListOf<Int>()
        val startTime = System.currentTimeMillis()
        var bestMedian = Double.MAX_VALUE
        var stableSince = 0L
        var lastError: Exception? = null

        while (coroutineContext.isActive) {
            val elapsed = System.currentTimeMillis() - startTime

            // Hard-stop at maxMs
            if (elapsed >= maxMs) break

            // Take a sample
            val rtt = try {
                healthApi.pingHealth()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = e
                continue
            }
            samples.add(rtt)
            SdkLogger.d(TAG, "Ping sample #${samples.size}: RTT=${rtt} ms")

            val currentMedian = PingCalculator.median(samples).toInt()
            onSample?.invoke(samples.toList(), currentMedian)

            // Stability check (need at least 8 samples for window)
            if (samples.size >= 8) {
                val recentWindow = samples.takeLast(8)
                val recentMedian = PingCalculator.median(recentWindow)

                if (recentMedian < bestMedian) {
                    bestMedian = recentMedian
                }

                val isStable = PingCalculator.isStable(recentWindow, bestMedian, 0.05)

                if (isStable) {
                    if (stableSince == 0L) stableSince = System.currentTimeMillis()
                    val stableDuration = System.currentTimeMillis() - stableSince
                    SdkLogger.d(TAG, "Stable for ${stableDuration} ms (need ${stableHoldMs} ms, samples=${samples.size}/${minSamples})")
                    if (stableDuration >= stableHoldMs && samples.size >= minSamples) {
                        SdkLogger.d(TAG, "Early-stop: stability reached")
                        break
                    }
                } else {
                    stableSince = 0L
                }
            }
        }

        if (samples.isEmpty()) {
            SdkLogger.w(TAG, "No ping samples collected")
            throw SpeedTestException(
                SpeedTestException.Reason.NO_PING_SAMPLES,
                "No ping samples collected: server unreachable or every /health request failed" +
                    (lastError?.message?.let { " ($it)" } ?: ""),
                lastError,
            )
        }

        SdkLogger.d(TAG, "Ping done — ${samples.size} samples, min=${samples.min()} ms, max=${samples.max()} ms")

        PingStats(
            avg = PingCalculator.trimmedMean(samples, 0.1),
            median = PingCalculator.median(samples),
            p95 = PingCalculator.percentile(samples, 0.95),
            jitter = PingCalculator.jitter(samples),
            min = samples.min(),
            max = samples.max(),
            samples = samples.toList(),
        )
    }

    companion object {
        private const val TAG = "PingEngine"
    }
}
