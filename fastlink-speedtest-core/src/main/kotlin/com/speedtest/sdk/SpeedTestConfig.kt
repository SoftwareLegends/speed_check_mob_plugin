package com.speedtest.sdk

/**
 * Configuration for the SpeedTest SDK.
 *
 * @param baseUrl The base URL of the speed test server (e.g., "https://speed.example.com")
 * @param username Optional HTTP Basic Auth username
 * @param password Optional HTTP Basic Auth password
 * @param pingTimeoutMs Maximum duration for the ping phase in milliseconds
 * @param downloadTimeoutMs Maximum duration for the download phase in milliseconds
 * @param uploadTimeoutMs Maximum duration for the upload phase in milliseconds
 * @param requestTimeoutMs Per-request timeout in milliseconds
 * @param overallTimeoutMs Timeout for the entire test in milliseconds
 */
data class SpeedTestConfig(
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val pingTimeoutMs: Long = 5_000,
    val downloadTimeoutMs: Long = 30_000,
    val uploadTimeoutMs: Long = 30_000,
    val requestTimeoutMs: Long = 30_000,
    val overallTimeoutMs: Long = 120_000,
)
