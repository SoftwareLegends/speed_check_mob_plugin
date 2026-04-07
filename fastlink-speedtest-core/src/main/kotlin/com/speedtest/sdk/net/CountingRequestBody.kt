package com.speedtest.sdk.net

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * OkHttp [RequestBody] wrapper that tracks bytes written progressively.
 *
 * @param data The raw byte payload to upload
 * @param contentType The media type of the payload
 * @param onProgress Called with each chunk of bytes written
 */
class CountingRequestBody(
    private val data: ByteArray,
    private val contentType: MediaType? = "application/octet-stream".toMediaType(),
    private val onProgress: (bytesWritten: Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = data.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        var offset = 0
        val bufferSize = 8192
        while (offset < data.size) {
            val toWrite = minOf(bufferSize, data.size - offset)
            sink.write(data, offset, toWrite)
            offset += toWrite
            onProgress(toWrite.toLong())
        }
    }
}
