package com.speedtest.sdk.engine

import com.speedtest.sdk.SpeedTestState
import com.speedtest.sdk.math.SpeedSampler
import com.speedtest.sdk.math.SustainedScorer
import com.speedtest.sdk.model.PhaseResult
import com.speedtest.sdk.model.Tier
import com.speedtest.sdk.net.UploadApi
import com.speedtest.sdk.util.SdkLogger
import kotlinx.coroutines.*

/**
 * Multi-connection upload engine with adaptive payload sizing and stability detection.
 */
class UploadEngine(
    private val uploadApi: UploadApi,
    private val tier: Tier,
    private val maxMs: Long = 30_000,
    private val onState: ((SpeedTestState.Uploading) -> Unit)? = null,
) {

    suspend fun run(): PhaseResult = coroutineScope {
        SdkLogger.d(TAG, "Starting upload — conn=${tier.conn}, maxConn=${tier.maxConn}, chunk=${tier.ulChunk / 1024}KB, maxPayload=${tier.ulMaxPayload / 1024}KB, maxMs=$maxMs")
        val sampler = SpeedSampler()
        val workers = mutableListOf<Job>()

        sampler.start()
        val startTime = System.currentTimeMillis()
        var lastRampTime = startTime
        var activeConns = tier.conn

        // Launch initial workers
        SdkLogger.d(TAG, "Launching $activeConns initial upload workers")
        repeat(tier.conn) {
            workers += launch(Dispatchers.IO) {
                UploadWorker(uploadApi, tier, sampler).run()
            }
        }

        // 200ms sampling loop
        try {
            while (isActive) {
                delay(200)
                val now = System.currentTimeMillis()
                val elapsed = now - startTime

                val currentMbps = sampler.sample()

                val progress = (elapsed.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)

                onState?.invoke(
                    SpeedTestState.Uploading(
                        currentMbps = currentMbps,
                        peakMbps = sampler.peakMbps,
                        elapsedMs = elapsed,
                        connections = activeConns,
                        progress = progress,
                    )
                )

                // Connection ramping
                if (now - lastRampTime >= tier.rampMs && activeConns < tier.maxConn) {
                    activeConns++
                    SdkLogger.d(TAG, "Ramping: launching upload worker #$activeConns at ${elapsed}ms")
                    workers += launch(Dispatchers.IO) {
                        UploadWorker(uploadApi, tier, sampler).run()
                    }
                    lastRampTime = now
                }

                // Stop conditions
                if (elapsed >= maxMs) {
                    SdkLogger.d(TAG, "Hard-stop: maxMs=$maxMs reached")
                    break
                }
                if (elapsed >= tier.minRunMs && sampler.isStable(3000)) {
                    SdkLogger.d(TAG, "Early-stop: stability reached at ${elapsed}ms")
                    // Emit a final 100% snapshot so the UI can show "complete"
                    onState?.invoke(
                        SpeedTestState.Uploading(
                            currentMbps = currentMbps,
                            peakMbps = sampler.peakMbps,
                            elapsedMs = elapsed,
                            connections = activeConns,
                            progress = 1f,
                        )
                    )
                    break
                }
            }
        } finally {
            workers.forEach { it.cancel() }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val scoringSamples = sampler.getScoringsamples(startTime, tier.warmupMs)

        val finalMbps = SustainedScorer.score(scoringSamples, sampler.peakMbps)
        SdkLogger.d(TAG, "Upload scoring: ${scoringSamples.size} scoring samples, peak=${sampler.peakMbps} Mbps, sustained=$finalMbps Mbps")

        val trimmedMean = if (scoringSamples.isNotEmpty()) {
            val speeds = scoringSamples.map { it.speedMbps }.sorted()
            val trimCount = (speeds.size * 0.1).toInt()
            val trimmed = speeds.subList(trimCount, speeds.size - trimCount)
            if (trimmed.isEmpty()) speeds.average() else trimmed.average()
        } else 0.0

        PhaseResult(
            finalMbps = finalMbps,
            peakMbps = sampler.peakMbps,
            trimmedMeanMbps = trimmedMean,
            durationMs = elapsed,
        )
    }

    companion object {
        private const val TAG = "ULEngine"
    }
}
