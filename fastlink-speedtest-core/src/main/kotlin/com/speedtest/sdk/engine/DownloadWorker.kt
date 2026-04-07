package com.speedtest.sdk.engine

import com.speedtest.sdk.math.SpeedSampler
import com.speedtest.sdk.model.Tier
import com.speedtest.sdk.net.DownloadApi
import com.speedtest.sdk.net.FilesApi
import com.speedtest.sdk.util.SdkLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * A single download connection that loops fetching random byte ranges.
 */
class DownloadWorker(
    private val downloadApi: DownloadApi,
    private val tier: Tier,
    private val sampler: SpeedSampler,
    private val fileIndex: AtomicInteger,
    private val fileSizes: Map<String, Long>,
) {

    suspend fun run() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(8192)

        while (coroutineContext.isActive) {
            try {
                // Pick file round-robin
                val idx = fileIndex.getAndIncrement() % tier.files.size
                val fileName = tier.files[idx]
                val fileSize = fileSizes[fileName] ?: continue

                // Random byte range
                val maxStart = fileSize - tier.dlChunk
                val startOffset = if (maxStart > 0) Random.nextLong(0, maxStart) else 0L
                val endOffset = startOffset + tier.dlChunk - 1

                SdkLogger.d(TAG, "Downloading $fileName range=$startOffset-$endOffset (${tier.dlChunk / 1024}KB)")
                downloadApi.downloadRange(fileName, startOffset, endOffset).use { rangeResponse ->
                    while (coroutineContext.isActive) {
                        val bytesRead = rangeResponse.stream.read(buffer)
                        if (bytesRead == -1) break
                        sampler.totalBytes.addAndGet(bytesRead.toLong())
                    }
                }
            } catch (e: Exception) {
                SdkLogger.w(TAG, "Download chunk failed: ${e.message}")
                if (!coroutineContext.isActive) break
            }
        }
    }

    companion object {
        private const val TAG = "DLWorker"
    }
}
