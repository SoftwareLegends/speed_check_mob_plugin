package com.speedtest.sdk.model

/**
 * Result of a download or upload phase.
 *
 * @param finalMbps Sustained bucket speed in Mbps
 * @param peakMbps Peak observed speed in Mbps
 * @param trimmedMeanMbps Trimmed mean of all speed samples
 * @param durationMs Duration of the phase in milliseconds
 */
data class PhaseResult(
    val finalMbps: Double,
    val peakMbps: Double,
    val trimmedMeanMbps: Double,
    val durationMs: Long,
)
