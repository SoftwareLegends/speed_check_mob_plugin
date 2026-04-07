package com.speedtest.sdk

import com.speedtest.sdk.model.PhaseResult
import com.speedtest.sdk.model.PingStats

/**
 * Final result of a complete speed test.
 *
 * @param ping Latency statistics
 * @param download Download phase result
 * @param upload Upload phase result
 * @param serverUrl The server URL tested against
 * @param timestamp System.currentTimeMillis() when the test completed
 */
data class SpeedTestResult(
    val ping: PingStats,
    val download: PhaseResult,
    val upload: PhaseResult,
    val serverUrl: String,
    val timestamp: Long,
)
