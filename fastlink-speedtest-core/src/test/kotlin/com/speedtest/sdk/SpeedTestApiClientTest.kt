package com.speedtest.sdk

import com.speedtest.sdk.net.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpeedTestApiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun baseUrl() = server.url("/").toString().removeSuffix("/")

    @Test
    fun `health endpoint returns RTT`() {
        server.enqueue(MockResponse().setBody("OK").setResponseCode(200))

        val client = SpeedTestApiClient.create(
            SpeedTestConfig(baseUrl = baseUrl())
        )
        val healthApi = HealthApi(client, baseUrl())
        val rtt = healthApi.pingHealth()

        assertTrue("RTT should be >= 0, was $rtt", rtt >= 0)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("/health"))
    }

    @Test
    fun `download with range returns 206 and validates`() {
        val body = ByteArray(1024) { it.toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-1023/1048576")
                .setBody(okio.Buffer().write(body))
        )

        val client = SpeedTestApiClient.create(SpeedTestConfig(baseUrl = baseUrl()))
        val downloadApi = DownloadApi(client, baseUrl())

        val response = downloadApi.downloadRange("test-1MB.bin", 0, 1023)
        response.use {
            val buffer = ByteArray(8192)
            var totalRead = 0L
            while (true) {
                val n = it.stream.read(buffer)
                if (n == -1) break
                totalRead += n
            }
            assertEquals(1024L, totalRead)
        }

        val request = server.takeRequest()
        assertEquals("bytes=0-1023", request.getHeader("Range"))
    }

    @Test
    fun `upload with speedcheck light returns 204`() {
        server.enqueue(MockResponse().setResponseCode(204))

        val client = SpeedTestApiClient.create(SpeedTestConfig(baseUrl = baseUrl()))
        val uploadApi = UploadApi(client, baseUrl())

        val payload = ByteArray(512)
        val body = CountingRequestBody(payload) { }
        val code = uploadApi.upload(body)

        assertEquals(204, code)
        val request = server.takeRequest()
        assertEquals("1", request.getHeader("X-Speedcheck-Light"))
        assertEquals("POST", request.method)
    }

    @Test
    fun `auth header added when credentials configured`() {
        server.enqueue(MockResponse().setBody("OK").setResponseCode(200))

        val config = SpeedTestConfig(
            baseUrl = baseUrl(),
            username = "user",
            password = "pass",
        )
        val client = SpeedTestApiClient.create(config)
        val healthApi = HealthApi(client, baseUrl())
        healthApi.pingHealth()

        val request = server.takeRequest()
        val auth = request.getHeader("Authorization")
        assertNotNull("Authorization header should be present", auth)
        assertTrue("Should be Basic auth", auth!!.startsWith("Basic "))
    }

    @Test
    fun `cache control no-store present on requests`() {
        server.enqueue(MockResponse().setBody("OK").setResponseCode(200))

        val client = SpeedTestApiClient.create(SpeedTestConfig(baseUrl = baseUrl()))
        val healthApi = HealthApi(client, baseUrl())
        healthApi.pingHealth()

        val request = server.takeRequest()
        assertEquals("no-store", request.getHeader("Cache-Control"))
    }

    @Test
    fun `download non-206 throws exception`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not partial"))

        val client = SpeedTestApiClient.create(SpeedTestConfig(baseUrl = baseUrl()))
        val downloadApi = DownloadApi(client, baseUrl())

        try {
            downloadApi.downloadRange("test-1MB.bin", 0, 1023)
            fail("Should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("206"))
        }
    }
}
