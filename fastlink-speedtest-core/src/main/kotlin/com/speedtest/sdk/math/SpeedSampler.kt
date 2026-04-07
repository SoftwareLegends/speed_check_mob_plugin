package com.speedtest.sdk.math

import com.speedtest.sdk.model.SpeedSample
import com.speedtest.sdk.util.EwmaFilter
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared speed sampler used by download and upload engines.
 * Tracks bytes transferred, computes EWMA-smoothed speed, and detects stability.
 */
class SpeedSampler {

    /** Shared byte counter incremented by all workers. */
    val totalBytes = AtomicLong(0L)

    private val samples = mutableListOf<SpeedSample>()
    private val ewmaFilter = EwmaFilter(alpha = 0.3)

    var peakMbps: Double = 0.0
        private set

    private var peakTimestamp: Long = 0L
    private var lastSampleBytes: Long = 0L
    private var lastSampleTime: Long = 0L
    private var stableSinceMs: Long = 0L
    private var wasStable = false

    /** Initialize timing. Must be called before the first [sample] call. */
    fun start() {
        val now = System.currentTimeMillis()
        lastSampleBytes = 0L
        lastSampleTime = now
        stableSinceMs = now
    }

    /**
     * Take a speed sample. Should be called every ~200ms.
     * @return The EWMA-smoothed speed in Mbps.
     */
    fun sample(): Double {
        val now = System.currentTimeMillis()
        val currentBytes = totalBytes.get()
        val deltaBytes = currentBytes - lastSampleBytes
        val deltaTime = now - lastSampleTime

        if (deltaTime <= 0) return samples.lastOrNull()?.speedMbps ?: 0.0

        val rawSpeedMbps = (deltaBytes * 8.0) / (deltaTime * 1000.0)
        val smoothedSpeed = ewmaFilter.update(rawSpeedMbps)

        samples.add(SpeedSample(smoothedSpeed, now))
        lastSampleBytes = currentBytes
        lastSampleTime = now

        // Peak tracking
        if (smoothedSpeed > peakMbps * 1.003) {
            peakMbps = smoothedSpeed
            peakTimestamp = now
        }

        // Stability tracking
        val currentlyStable = smoothedSpeed >= peakMbps * 0.95 && smoothedSpeed <= peakMbps * 1.003
        if (!currentlyStable) {
            stableSinceMs = now
            wasStable = false
        } else if (!wasStable) {
            stableSinceMs = now
            wasStable = true
        }

        return smoothedSpeed
    }

    /**
     * Check if speed has been stable for at least [holdMs] milliseconds.
     */
    fun isStable(holdMs: Long = 3000): Boolean {
        if (!wasStable) return false
        return (System.currentTimeMillis() - stableSinceMs) >= holdMs
    }

    /** Get all collected samples. */
    fun getSamples(): List<SpeedSample> = samples.toList()

    /**
     * Get samples collected after the warmup period.
     * @param startTime The test start time
     * @param warmupMs The warmup duration to skip
     */
    fun getScoringsamples(startTime: Long, warmupMs: Long): List<SpeedSample> {
        val warmupEnd = startTime + warmupMs
        return samples.filter { it.timestampMs >= warmupEnd }
    }

    /** Reset the sampler for reuse. */
    fun reset() {
        totalBytes.set(0L)
        samples.clear()
        ewmaFilter.reset()
        peakMbps = 0.0
        peakTimestamp = 0L
        lastSampleBytes = 0L
        lastSampleTime = 0L
        stableSinceMs = 0L
        wasStable = false
    }
}
