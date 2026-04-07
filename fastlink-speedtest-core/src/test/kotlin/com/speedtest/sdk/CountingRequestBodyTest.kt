package com.speedtest.sdk

import com.speedtest.sdk.net.CountingRequestBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class CountingRequestBodyTest {

    @Test
    fun `progress callback tracks correct byte counts`() {
        val data = ByteArray(20000) { it.toByte() }
        val progressChunks = mutableListOf<Long>()

        val body = CountingRequestBody(data) { bytesWritten ->
            progressChunks.add(bytesWritten)
        }

        val buffer = Buffer()
        body.writeTo(buffer)

        // Total reported bytes should equal data size
        assertEquals(data.size.toLong(), progressChunks.sum())

        // Each chunk should be <= 8192 bytes
        assertTrue(progressChunks.all { it <= 8192 })
    }

    @Test
    fun `content length reports correctly`() {
        val data = ByteArray(1024)
        val body = CountingRequestBody(data) { }
        assertEquals(1024L, body.contentLength())
    }

    @Test
    fun `total bytes written matches input size`() {
        val data = ByteArray(50000) { it.toByte() }
        val body = CountingRequestBody(data) { }
        val buffer = Buffer()
        body.writeTo(buffer)
        assertEquals(data.size.toLong(), buffer.size)
    }

    @Test
    fun `small payload writes in single chunk`() {
        val data = ByteArray(100)
        val chunks = mutableListOf<Long>()
        val body = CountingRequestBody(data) { chunks.add(it) }
        val buffer = Buffer()
        body.writeTo(buffer)

        assertEquals(1, chunks.size)
        assertEquals(100L, chunks[0])
    }

    @Test
    fun `content type is octet stream by default`() {
        val body = CountingRequestBody(ByteArray(10)) { }
        assertEquals("application/octet-stream", body.contentType()?.toString())
    }
}
