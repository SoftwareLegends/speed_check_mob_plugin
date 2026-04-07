package com.speedtest.sdk.engine

import com.speedtest.sdk.math.SpeedSampler
import com.speedtest.sdk.model.Tier
import com.speedtest.sdk.net.CountingRequestBody
import com.speedtest.sdk.net.UploadApi
import com.speedtest.sdk.util.PayloadCache
import com.speedtest.sdk.util.SdkLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * A single upload connection that loops posting random payloads with adaptive sizing.
 */
class UploadWorker(
    private val uploadApi: UploadApi,
    private val tier: Tier,
    private val sampler: SpeedSampler,
) {
    private val floorSize = max(65536L, min(tier.ulChunk, 262144L))
    private val hardMaxPayload = 8_388_608L // 8 MB

    suspend fun run() = withContext(Dispatchers.IO) {
        var payloadSize = tier.ulChunk

        while (coroutineContext.isActive) {
            try {
                val payload = PayloadCache.get(payloadSize.toInt())
                val body = CountingRequestBody(payload) { bytesWritten ->
                    sampler.totalBytes.addAndGet(bytesWritten)
                }

                val startTime = System.currentTimeMillis()
                uploadApi.upload(body)
                val duration = System.currentTimeMillis() - startTime

                // Adaptive sizing
                val prevSize = payloadSize
                payloadSize = when {
                    duration < 450 -> min(payloadSize * 2, min(tier.ulMaxPayload, hardMaxPayload))
                    duration > 3000 -> max(payloadSize / 2, floorSize)
                    else -> payloadSize
                }
                if (payloadSize != prevSize) {
                    SdkLogger.d(TAG, "Adaptive resize: ${prevSize / 1024}KB -> ${payloadSize / 1024}KB (duration=${duration}ms)")
                }
            } catch (e: Exception) {
                if (!coroutineContext.isActive) break
                val prevSize = payloadSize
                payloadSize = max(payloadSize / 2, floorSize)
                SdkLogger.w(TAG, "Upload failed, resize ${prevSize / 1024}KB -> ${payloadSize / 1024}KB: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ULWorker"
    }
}
