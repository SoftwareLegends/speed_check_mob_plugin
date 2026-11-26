package com.speedtest.sdk.net

import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.SpeedTestProtocol
import com.speedtest.sdk.util.SdkLogger
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * Factory for creating a configured OkHttpClient for speed test measurements.
 */
object SpeedTestApiClient {

    /**
     * Create an OkHttpClient configured for the given [SpeedTestConfig].
     * Uses HTTP/1.1 by default so N workers = N TCP connections, and adds auth + cache control interceptors.
     */
    fun create(config: SpeedTestConfig): OkHttpClient {
        val isHttps = config.baseUrl.startsWith("https", ignoreCase = true)
        val hasAuth = !config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()

        SdkLogger.d(TAG, "Creating OkHttpClient — baseUrl=${config.baseUrl}, https=$isHttps, auth=${if (hasAuth) "enabled" else "disabled"}")
        if (!hasAuth) {
            SdkLogger.w(TAG, "No credentials provided. If the server requires Basic Auth, requests will return 401.")
        }

        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
            .protocols(protocolsFor(config.protocol, isHttps))
            .dispatcher(Dispatcher().apply {
                maxRequests = MAX_PARALLEL_CALLS
                maxRequestsPerHost = MAX_PARALLEL_CALLS
            })
            .connectionPool(ConnectionPool(MAX_PARALLEL_CALLS, 5, TimeUnit.MINUTES))
            .apply {
                // Add auth interceptor if credentials provided.
                // Server requires Basic Auth on EVERY request (/health, /api/files,
                // /download/*, /upload). The interceptor applies it globally.
                if (hasAuth) {
                    val credential = Credentials.basic(config.username!!, config.password!!)
                    addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Authorization", credential)
                                .build()
                        )
                    }
                }
            }
            // Add Cache-Control for all measurement requests
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Cache-Control", "no-store")
                    .build()
                val response = chain.proceed(request)
                if (response.code == 401) {
                    SdkLogger.e(
                        TAG,
                        "401 Unauthorized for ${request.url} — check baseUrl, username, and password in SpeedTestConfig"
                    )
                }
                response
            }
            .build()
    }

    /** Must be >= the largest tier maxConn (20). */
    internal const val MAX_PARALLEL_CALLS = 32

    internal fun protocolsFor(requested: SpeedTestProtocol, isHttps: Boolean): List<Protocol> = when (requested) {
        SpeedTestProtocol.H2_PRIOR_KNOWLEDGE -> listOf(Protocol.H2_PRIOR_KNOWLEDGE)
        // HTTP_2 over cleartext is not possible without prior knowledge; fall back to HTTP/1.1.
        SpeedTestProtocol.HTTP_2 -> if (isHttps) listOf(Protocol.HTTP_2, Protocol.HTTP_1_1) else listOf(Protocol.HTTP_1_1)
        else -> listOf(Protocol.HTTP_1_1)
    }

    private const val TAG = "ApiClient"
}
