package com.speedtest.sdk.engine

import com.speedtest.sdk.net.DownloadApi
import com.speedtest.sdk.util.SdkLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs 1-2 probe downloads to estimate connection speed for tier selection.
 *
 * Algorithm:
 * 1. Micro probe: 256 KB from test-1MB.bin
 * 2. If >= 10 Mbps, second probe: 4 MB from test-5MB.bin
 */
class ProbeEngine(
    private val downloadApi: DownloadApi,
) {

    suspend fun run(): Double = withContext(Dispatchers.IO) {
        // Micro probe: 256 KB
        SdkLogger.d(TAG, "Micro probe: 256 KB from test-1MB.bin")
        val microMbps = probeRange("test-1MB.bin", 0, 262143)
        SdkLogger.d(TAG, "Micro probe result: $microMbps Mbps")

        if (microMbps >= 10.0) {
            // Second probe: 4 MB
            SdkLogger.d(TAG, "Speed >= 10 Mbps, running second probe: 4 MB from test-5MB.bin")
            val fullMbps = probeRange("test-5MB.bin", 0, 4194303)
            SdkLogger.d(TAG, "Second probe result: $fullMbps Mbps")
            fullMbps
        } else {
            SdkLogger.d(TAG, "Speed < 10 Mbps, skipping second probe")
            microMbps
        }
    }

    private fun probeRange(fileName: String, start: Long, end: Long): Double {
        val expectedBytes = end - start + 1
        val startTime = System.nanoTime()

        downloadApi.downloadRange(fileName, start, end).use { rangeResponse ->
            val buffer = ByteArray(8192)
            var totalRead = 0L
            while (true) {
                val bytesRead = rangeResponse.stream.read(buffer)
                if (bytesRead == -1) break
                totalRead += bytesRead
            }
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
            SdkLogger.d(TAG, "Probe $fileName: read $totalRead bytes in ${"%.1f".format(elapsedMs)} ms")
            if (elapsedMs <= 0) return 0.0
            return (totalRead * 8.0) / (elapsedMs * 1000.0)
        }
    }

    companion object {
        private const val TAG = "ProbeEngine"
    }
}
