package com.speedtest.sdk.net

import com.speedtest.sdk.util.SdkLogger
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * API for health check / ping endpoints.
 */
class HealthApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {

    /**
     * Pre-warm the connection with a HEAD request.
     */
    fun headHealth() {
        SdkLogger.d(TAG, "HEAD /health")
        val request = Request.Builder()
            .url("$baseUrl/health")
            .head()
            .build()
        client.newCall(request).execute().use { response ->
            SdkLogger.d(TAG, "HEAD /health -> ${response.code}")
        }
    }

    /**
     * Send a GET /health request and return the RTT in milliseconds.
     */
    fun pingHealth(): Int {
        val request = Request.Builder()
            .url("$baseUrl/health")
            .get()
            .build()

        val startNanos = System.nanoTime()
        client.newCall(request).execute().use { response ->
            response.body?.string() // consume body
            val elapsedNanos = System.nanoTime() - startNanos
            return (elapsedNanos / 1_000_000).toInt()
        }
    }

    companion object {
        private const val TAG = "HealthApi"
    }
}
