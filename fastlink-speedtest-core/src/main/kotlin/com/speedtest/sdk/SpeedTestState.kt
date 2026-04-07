package com.speedtest.sdk

/**
 * Sealed interface representing all possible states during a speed test.
 * Observe via [SpeedTestClient.state] for real-time UI updates.
 */
sealed interface SpeedTestState {

    /** No test is running. */
    data object Idle : SpeedTestState

    /** Establishing connection to the server. */
    data object Connecting : SpeedTestState

    /** Measuring latency to the server. */
    data class MeasuringPing(
        val samples: List<Int>,
        val currentMedian: Int,
    ) : SpeedTestState

    /** Running a probe download to determine connection tier. */
    data object Probing : SpeedTestState

    /**
     * Download measurement in progress.
     * @param progress Phase completion in [0f, 1f]. Reaches 1f at hard-stop or early-stop.
     */
    data class Downloading(
        val currentMbps: Double,
        val peakMbps: Double,
        val elapsedMs: Long,
        val connections: Int,
        val progress: Float = 0f,
    ) : SpeedTestState

    /**
     * Upload measurement in progress.
     * @param progress Phase completion in [0f, 1f]. Reaches 1f at hard-stop or early-stop.
     */
    data class Uploading(
        val currentMbps: Double,
        val peakMbps: Double,
        val elapsedMs: Long,
        val connections: Int,
        val progress: Float = 0f,
    ) : SpeedTestState

    /** Test completed successfully. */
    data class Finished(val result: SpeedTestResult) : SpeedTestState

    /** An error occurred during a specific phase. */
    data class Error(val phase: String, val throwable: Throwable) : SpeedTestState
}
