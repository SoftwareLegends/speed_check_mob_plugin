package com.speedtest.sdk

/**
 * Thrown when a speed test phase cannot produce a meaningful measurement.
 * Also delivered as [SpeedTestState.Error.throwable].
 */
class SpeedTestException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Reason {
        /** Every ping request failed. */
        NO_PING_SAMPLES,

        /** A download/upload/probe phase transferred zero bytes. */
        NO_DATA_TRANSFERRED,
    }
}
