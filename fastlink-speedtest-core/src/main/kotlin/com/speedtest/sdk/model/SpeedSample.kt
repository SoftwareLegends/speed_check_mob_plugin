package com.speedtest.sdk.model

/**
 * A single speed measurement sample.
 *
 * @param speedMbps Speed in megabits per second
 * @param timestampMs System timestamp when sample was taken
 */
data class SpeedSample(
    val speedMbps: Double,
    val timestampMs: Long,
)
