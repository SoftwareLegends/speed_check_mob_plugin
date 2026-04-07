package com.speedtest.sdk.math

import kotlin.math.abs
import kotlin.math.floor

/**
 * Pure functions for computing ping statistics.
 */
object PingCalculator {

    /**
     * Trimmed mean: sort samples, drop [trimPercent] from each end, average the rest.
     */
    fun trimmedMean(samples: List<Int>, trimPercent: Double = 0.1): Double {
        if (samples.isEmpty()) return 0.0
        if (samples.size == 1) return samples[0].toDouble()

        val sorted = samples.sorted()
        val trimCount = (sorted.size * trimPercent).toInt()
        val trimmed = sorted.subList(trimCount, sorted.size - trimCount)

        return if (trimmed.isEmpty()) {
            sorted.average()
        } else {
            trimmed.average()
        }
    }

    /**
     * Median of integer samples.
     */
    fun median(samples: List<Int>): Double {
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid].toDouble()
        }
    }

    /**
     * Percentile value using linear interpolation.
     */
    fun percentile(samples: List<Int>, p: Double): Double {
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        if (sorted.size == 1) return sorted[0].toDouble()

        val index = p * (sorted.size - 1)
        val lower = floor(index).toInt()
        val upper = lower + 1
        val fraction = index - lower

        return if (upper >= sorted.size) {
            sorted.last().toDouble()
        } else {
            sorted[lower] + fraction * (sorted[upper] - sorted[lower])
        }
    }

    /**
     * Jitter: median of absolute deltas between consecutive samples.
     */
    fun jitter(samples: List<Int>): Double {
        if (samples.size < 2) return 0.0
        val deltas = (1 until samples.size).map { i ->
            abs(samples[i] - samples[i - 1])
        }
        return median(deltas)
    }

    /**
     * Check if the recent samples median is within [tolerancePercent] of [bestMedian].
     */
    fun isStable(
        recentSamples: List<Int>,
        bestMedian: Double,
        tolerancePercent: Double = 0.05,
    ): Boolean {
        if (recentSamples.isEmpty() || bestMedian <= 0.0) return false
        val currentMedian = median(recentSamples)
        val diff = abs(currentMedian - bestMedian) / bestMedian
        return diff <= tolerancePercent
    }
}
