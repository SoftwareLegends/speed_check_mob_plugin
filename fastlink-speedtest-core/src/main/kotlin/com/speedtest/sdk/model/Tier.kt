package com.speedtest.sdk.model

/**
 * A speed tier that determines test parameters based on the probed connection speed.
 *
 * @param maxMbps Upper bound of this tier in Mbps (Double.MAX_VALUE for the highest tier)
 * @param conn Initial number of connections
 * @param maxConn Maximum connections after ramping
 * @param dlChunk Download chunk size in bytes
 * @param ulChunk Upload chunk size in bytes
 * @param ulMaxPayload Maximum upload payload per request in bytes
 * @param warmupMs Warmup period in ms (samples during warmup are not scored)
 * @param minRunMs Minimum run time before early-stop is allowed
 * @param rampMs Interval for adding new connections
 * @param files Pool of test files for this tier
 */
data class Tier(
    val maxMbps: Double,
    val conn: Int,
    val maxConn: Int,
    val dlChunk: Long,
    val ulChunk: Long,
    val ulMaxPayload: Long,
    val warmupMs: Long,
    val minRunMs: Long,
    val rampMs: Long,
    val files: List<String>,
) {
    companion object {
        private const val KB = 1024L
        private const val MB = 1024L * 1024L

        /** Complete tier table per specification. */
        val TABLE: List<Tier> = listOf(
            Tier(
                maxMbps = 0.8, conn = 1, maxConn = 2,
                dlChunk = 64 * KB, ulChunk = 64 * KB, ulMaxPayload = 128 * KB,
                warmupMs = 1200, minRunMs = 12000, rampMs = 2600,
                files = listOf("test-1MB.bin", "test-5MB.bin"),
            ),
            Tier(
                maxMbps = 3.0, conn = 2, maxConn = 4,
                dlChunk = 128 * KB, ulChunk = 128 * KB, ulMaxPayload = 256 * KB,
                warmupMs = 1100, minRunMs = 11000, rampMs = 2200,
                files = listOf("test-5MB.bin", "test-10MB.bin"),
            ),
            Tier(
                maxMbps = 12.0, conn = 3, maxConn = 6,
                dlChunk = 512 * KB, ulChunk = 256 * KB, ulMaxPayload = 512 * KB,
                warmupMs = 950, minRunMs = 10000, rampMs = 1700,
                files = listOf("test-10MB.bin", "test-25MB.bin"),
            ),
            Tier(
                maxMbps = 60.0, conn = 6, maxConn = 10,
                dlChunk = 1 * MB, ulChunk = 512 * KB, ulMaxPayload = 1 * MB,
                warmupMs = 900, minRunMs = 9000, rampMs = 1300,
                files = listOf("test-50MB.bin", "test-100MB.bin"),
            ),
            Tier(
                maxMbps = 250.0, conn = 8, maxConn = 12,
                dlChunk = 2 * MB, ulChunk = 1 * MB, ulMaxPayload = 2 * MB,
                warmupMs = 1000, minRunMs = 10000, rampMs = 1000,
                files = listOf("test-100MB.bin", "test-200MB.bin", "test-300MB.bin"),
            ),
            Tier(
                maxMbps = 1000.0, conn = 10, maxConn = 16,
                dlChunk = 4 * MB, ulChunk = 2 * MB, ulMaxPayload = 4 * MB,
                warmupMs = 1200, minRunMs = 12000, rampMs = 900,
                files = listOf("test-300MB.bin", "test-500MB.bin", "test-1024MB.bin"),
            ),
            Tier(
                maxMbps = Double.MAX_VALUE, conn = 12, maxConn = 20,
                dlChunk = 6 * MB, ulChunk = 4 * MB, ulMaxPayload = 8 * MB,
                warmupMs = 1500, minRunMs = 14000, rampMs = 800,
                files = listOf("test-500MB.bin", "test-1024MB.bin"),
            ),
        )
    }
}
