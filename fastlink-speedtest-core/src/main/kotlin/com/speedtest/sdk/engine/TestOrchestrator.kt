package com.speedtest.sdk.engine

import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.SpeedTestState
import com.speedtest.sdk.model.PhaseResult
import com.speedtest.sdk.model.PingStats
import com.speedtest.sdk.model.TierSelector
import com.speedtest.sdk.net.*
import com.speedtest.sdk.util.SdkLogger
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Internal orchestrator that sequences all speed test phases.
 */
internal class TestOrchestrator(
    private val config: SpeedTestConfig,
    private val stateFlow: MutableStateFlow<SpeedTestState>,
) {
    private val httpClient = SpeedTestApiClient.create(config)
    private val healthApi = HealthApi(httpClient, config.baseUrl)
    private val filesApi = FilesApi(httpClient, config.baseUrl)
    private val downloadApi = DownloadApi(httpClient, config.baseUrl)
    private val uploadApi = UploadApi(httpClient, config.baseUrl)

    private var filesSizeMap: Map<String, Long> = FilesApi.KNOWN_FILE_SIZES

    suspend fun runFullTest(): SpeedTestResult {
        // 1. Connecting — pre-warm
        SdkLogger.d(TAG, "Starting full test — pre-warming connection to ${config.baseUrl}")
        stateFlow.value = SpeedTestState.Connecting
        try {
            healthApi.headHealth()
            SdkLogger.d(TAG, "Pre-warm HEAD /health succeeded")
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Pre-warm HEAD /health failed: ${e.message}", e)
        }

        // Fetch file list
        try {
            val files = filesApi.getFiles()
            filesSizeMap = files.associate { it.name to it.size }
            SdkLogger.d(TAG, "Fetched file list: ${files.size} files available")
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Failed to fetch file list, using fallback sizes: ${e.message}")
        }

        // 2. Ping
        SdkLogger.d(TAG, "=== Phase: PING ===")
        val pingStats = runPing()
        SdkLogger.d(TAG, "Ping complete — avg=${pingStats.avg} ms, median=${pingStats.median} ms, jitter=${pingStats.jitter} ms, samples=${pingStats.samples.size}")

        // 3. Probe
        SdkLogger.d(TAG, "=== Phase: PROBE ===")
        val probeMbps = runProbe()
        SdkLogger.d(TAG, "Probe complete — speed=${probeMbps} Mbps")

        // 4. Download
        val dlTier = TierSelector.pickTier(probeMbps)
        SdkLogger.d(TAG, "=== Phase: DOWNLOAD === tier maxMbps=${dlTier.maxMbps}, conn=${dlTier.conn}->${dlTier.maxConn}, chunk=${dlTier.dlChunk / 1024}KB, files=${dlTier.files}")
        val dlResult = runDownload(dlTier, config.downloadTimeoutMs)
        SdkLogger.d(TAG, "Download complete — final=${dlResult.finalMbps} Mbps, peak=${dlResult.peakMbps} Mbps, duration=${dlResult.durationMs} ms")

        // 5. Upload — tier based on dl speed * 0.6
        val ulProbeMbps = dlResult.finalMbps * 0.6
        val ulTier = TierSelector.pickTier(ulProbeMbps)
        SdkLogger.d(TAG, "=== Phase: UPLOAD === ulProbe=${ulProbeMbps} Mbps, tier maxMbps=${ulTier.maxMbps}, conn=${ulTier.conn}->${ulTier.maxConn}, chunk=${ulTier.ulChunk / 1024}KB")
        val ulResult = runUpload(ulTier, config.uploadTimeoutMs)
        SdkLogger.d(TAG, "Upload complete — final=${ulResult.finalMbps} Mbps, peak=${ulResult.peakMbps} Mbps, duration=${ulResult.durationMs} ms")

        // 6. Build result
        val result = SpeedTestResult(
            ping = pingStats,
            download = dlResult,
            upload = ulResult,
            serverUrl = config.baseUrl,
            timestamp = System.currentTimeMillis(),
        )

        SdkLogger.d(TAG, "=== TEST COMPLETE === DL=${dlResult.finalMbps} Mbps, UL=${ulResult.finalMbps} Mbps, Ping=${pingStats.avg} ms")
        stateFlow.value = SpeedTestState.Finished(result)
        return result
    }

    suspend fun runPing(): PingStats {
        stateFlow.value = SpeedTestState.MeasuringPing(emptyList(), 0)
        return try {
            PingEngine(
                healthApi = healthApi,
                maxMs = config.pingTimeoutMs,
                onSample = { samples, median ->
                    stateFlow.value = SpeedTestState.MeasuringPing(samples, median)
                }
            ).run()
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Ping phase failed: ${e.message}", e)
            stateFlow.value = SpeedTestState.Error("ping", e)
            throw e
        }
    }

    suspend fun runProbe(): Double {
        stateFlow.value = SpeedTestState.Probing
        return try {
            ProbeEngine(downloadApi).run()
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Probe phase failed: ${e.message}", e)
            stateFlow.value = SpeedTestState.Error("probe", e)
            throw e
        }
    }

    suspend fun runDownload(
        tier: com.speedtest.sdk.model.Tier,
        maxMs: Long,
    ): PhaseResult {
        return try {
            DownloadEngine(
                downloadApi = downloadApi,
                tier = tier,
                fileSizes = filesSizeMap,
                maxMs = maxMs,
                onState = { stateFlow.value = it }
            ).run()
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Download phase failed: ${e.message}", e)
            stateFlow.value = SpeedTestState.Error("download", e)
            throw e
        }
    }

    suspend fun runUpload(
        tier: com.speedtest.sdk.model.Tier,
        maxMs: Long,
    ): PhaseResult {
        return try {
            UploadEngine(
                uploadApi = uploadApi,
                tier = tier,
                maxMs = maxMs,
                onState = { stateFlow.value = it }
            ).run()
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Upload phase failed: ${e.message}", e)
            stateFlow.value = SpeedTestState.Error("upload", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "Orchestrator"
    }
}
