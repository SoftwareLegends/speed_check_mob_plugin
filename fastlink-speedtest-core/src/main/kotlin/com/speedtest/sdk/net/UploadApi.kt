package com.speedtest.sdk.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * API for uploading test payloads to the server.
 */
class UploadApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    private val octetStream = "application/octet-stream".toMediaType()

    /**
     * Upload a payload with progress tracking.
     *
     * @param body The [CountingRequestBody] wrapping the payload
     * @return The response code
     */
    fun upload(body: CountingRequestBody): Int {
        val request = Request.Builder()
            .url("$baseUrl/upload")
            .header("X-Speedcheck-Light", "1")
            .header("Cache-Control", "no-store")
            .header("Content-Type", "application/octet-stream")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            return response.code
        }
    }
}
