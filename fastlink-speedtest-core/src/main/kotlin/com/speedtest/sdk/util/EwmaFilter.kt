package com.speedtest.sdk.util

/**
 * Exponential Weighted Moving Average filter for smoothing speed measurements.
 *
 * @param alpha Smoothing factor (0..1). Higher values give more weight to recent samples.
 */
class EwmaFilter(private val alpha: Double = 0.3) {
    private var value: Double? = null

    fun update(sample: Double): Double {
        val current = value
        val result = if (current == null) sample else alpha * sample + (1 - alpha) * current
        value = result
        return result
    }

    fun reset() {
        value = null
    }
}
