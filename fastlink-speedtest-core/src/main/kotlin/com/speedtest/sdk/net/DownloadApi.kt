package com.speedtest.sdk.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream

/**
 * API for downloading test files with HTTP Range support.
 */
class DownloadApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {

    /**
     * Download a byte range from a test file.
     *
     * @param fileName The test file name (e.g., "test-10MB.bin")
     * @param startByte Start of the byte range (inclusive)
     * @param endByte End of the byte range (inclusive)
     * @return A [RangeResponse] with the input stream and response for streaming
     * @throws IllegalStateException if the server doesn't return 206 or Content-Range is invalid
     */
    fun downloadRange(fileName: String, startByte: Long, endByte: Long): RangeResponse {
        val request = Request.Builder()
            .url("$baseUrl/download/$fileName")
            .header("Range", "bytes=$startByte-$endByte")
            .header("Cache-Control", "no-store")
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (response.code != 206) {
            response.close()
            throw IllegalStateException(
                "Expected 206 Partial Content, got ${response.code} for $fileName"
            )
        }

        val contentRange = response.header("Content-Range")
        if (contentRange == null) {
            response.close()
            throw IllegalStateException("Missing Content-Range header for $fileName")
        }

        val body = response.body
            ?: throw IllegalStateException("Empty response body for $fileName").also { response.close() }

        return RangeResponse(
            stream = body.byteStream(),
            response = response,
            contentLength = body.contentLength(),
        )
    }

    data class RangeResponse(
        val stream: InputStream,
        val response: Response,
        val contentLength: Long,
    ) : AutoCloseable {
        override fun close() {
            stream.close()
            response.close()
        }
    }
}
