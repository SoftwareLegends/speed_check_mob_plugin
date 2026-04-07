package com.speedtest.sdk.model

/**
 * Statistics from the ping measurement phase.
 *
 * @param avg Trimmed mean (drop 10% low + 10% high)
 * @param median Median RTT in ms
 * @param p95 95th percentile RTT in ms
 * @param jitter Median absolute delta between consecutive samples
 * @param min Minimum RTT in ms
 * @param max Maximum RTT in ms
 * @param samples Raw RTT samples in ms
 */
data class PingStats(
    val avg: Double,
    val median: Double,
    val p95: Double,
    val jitter: Double,
    val min: Int,
    val max: Int,
    val samples: List<Int>,
)
