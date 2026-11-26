package com.speedtest.sdk.bridge

import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.SpeedTestState
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Converts [SpeedTestState] / [SpeedTestResult] into plain maps (String -> primitives / nested maps)
 * that a JS bridge (React Native / Expo) can serialise directly.
 *
 * Event keys: `phase` (always), plus `currentMbps`, `peakMbps`, `progress`, `pingMs`, `jitterMs`,
 * `result`, `error` depending on the phase. Credentials are never part of any event.
 */
object SpeedTestEventMapper {

    const val CODE_CANCELLED = "CANCELLED"
    const val CODE_TIMEOUT = "TIMEOUT"
    const val CODE_NETWORK = "NETWORK"
    const val CODE_UNKNOWN = "UNKNOWN"
    const val CODE_BUSY = "BUSY"

    private val userInfoRegex = Regex("(?i)(https?://)[^/@\\s]+@")

    fun toEvent(state: SpeedTestState): Map<String, Any?> = when (state) {
        SpeedTestState.Idle -> mapOf("phase" to "idle")
        SpeedTestState.Connecting -> mapOf("phase" to "connecting")
        is SpeedTestState.MeasuringPing -> mapOf(
            "phase" to "ping",
            "pingMs" to state.currentMedian.toDouble(),
        )
        SpeedTestState.Probing -> mapOf("phase" to "probe")
        is SpeedTestState.Downloading -> mapOf(
            "phase" to "download",
            "currentMbps" to state.currentMbps,
            "peakMbps" to state.peakMbps,
            "progress" to state.progress.toDouble().coerceIn(0.0, 1.0),
        )
        is SpeedTestState.Uploading -> mapOf(
            "phase" to "upload",
            "currentMbps" to state.currentMbps,
            "peakMbps" to state.peakMbps,
            "progress" to state.progress.toDouble().coerceIn(0.0, 1.0),
        )
        is SpeedTestState.Finished -> mapOf(
            "phase" to "finished",
            "result" to toResult(state.result),
        )
        is SpeedTestState.Error -> errorEvent(state.phase, state.throwable)
    }

    fun toResult(r: SpeedTestResult): Map<String, Any?> = mapOf(
        "download" to r.download.finalMbps,
        "upload" to r.upload.finalMbps,
        "ping" to r.ping.avg,
        "jitter" to r.ping.jitter,
        "downloadPeak" to r.download.peakMbps,
        "uploadPeak" to r.upload.peakMbps,
        "serverUrl" to sanitize(r.serverUrl),
        "timestamp" to r.timestamp.toDouble(),
    )

    fun cancelledEvent(): Map<String, Any?> = mapOf("phase" to "cancelled")

    fun errorEvent(phase: String, t: Throwable): Map<String, Any?> = mapOf(
        "phase" to "error",
        "error" to errorMap(phase, t),
    )

    fun errorMap(phase: String, t: Throwable): Map<String, Any?> = mapOf(
        "phase" to phase,
        "code" to codeFor(t),
        "message" to sanitize(t.message ?: t.javaClass.simpleName),
    )

    fun codeFor(t: Throwable): String = when (t) {
        is TimeoutCancellationException, is SocketTimeoutException -> CODE_TIMEOUT
        is kotlinx.coroutines.CancellationException -> CODE_CANCELLED
        is IOException -> CODE_NETWORK
        else -> CODE_UNKNOWN
    }

    /** Strips `user:pass@` userinfo from URLs inside messages. */
    fun sanitize(s: String): String = userInfoRegex.replace(s, "$1")
}
