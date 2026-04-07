package com.speedtest.sdk.math

import com.speedtest.sdk.model.SpeedSample
import kotlin.math.floor
import kotlin.math.min

/**
 * Computes the sustained speed score using 0.5 Mbps buckets.
 *
 * The highest bucket with >= 3 seconds of cumulative time is the sustained speed.
 */
object SustainedScorer {

    private const val BUCKET_WIDTH = 0.5
    private const val MIN_SUSTAINED_MS = 3000L

    /**
     * Compute the final sustained speed.
     *
     * @param samples Speed samples with timestamps
     * @param peakMbps Observed peak speed for clamping
     * @return Final speed in Mbps
     */
    fun score(samples: List<SpeedSample>, peakMbps: Double): Double {
        if (samples.isEmpty()) return 0.0
        if (samples.size == 1) return min(samples[0].speedMbps, peakMbps)

        // Bucket all samples by 0.5 Mbps steps, accumulating time deltas
        val bucketTime = mutableMapOf<Int, Long>()

        for (i in 0 until samples.size - 1) {
            val bucketIndex = floor(samples[i].speedMbps / BUCKET_WIDTH).toInt()
            val dt = samples[i + 1].timestampMs - samples[i].timestampMs
            if (bucketIndex >= 0 && dt > 0) {
                bucketTime[bucketIndex] = (bucketTime[bucketIndex] ?: 0L) + dt
            }
        }

        // Find highest bucket with >= 3 seconds cumulative time
        val sustained = bucketTime.entries
            .filter { it.value >= MIN_SUSTAINED_MS }
            .maxByOrNull { it.key }

        val sustainedMbps = if (sustained != null) {
            sustained.key * BUCKET_WIDTH
        } else {
            // Fallback chain: peak → trimmed mean
            null
        }

        return when {
            sustainedMbps != null -> min(sustainedMbps, peakMbps)
            peakMbps > 0.0 -> peakMbps
            else -> trimmedMean(samples.map { it.speedMbps })
        }
    }

    private fun trimmedMean(values: List<Double>, trimPercent: Double = 0.1): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values[0]

        val sorted = values.sorted()
        val trimCount = (sorted.size * trimPercent).toInt()
        val trimmed = sorted.subList(trimCount, sorted.size - trimCount)

        return if (trimmed.isEmpty()) sorted.average() else trimmed.average()
    }
}
