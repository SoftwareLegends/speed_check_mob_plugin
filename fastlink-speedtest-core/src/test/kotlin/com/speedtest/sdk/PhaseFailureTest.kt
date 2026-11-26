package com.speedtest.sdk

import com.speedtest.sdk.math.SpeedSampler
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhaseFailureTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun client(downloadTimeoutMs: Long = 1_500) = SpeedTestClient(
        SpeedTestConfig(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            pingTimeoutMs = 700,
            downloadTimeoutMs = downloadTimeoutMs,
            uploadTimeoutMs = 1_500,
        )
    )

    @Test
    fun `server returning 500 for everything throws and ends in Error ping`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
        }
        server.start()
        val c = client()

        val e = try {
            runBlocking { c.runFullTest() }
            fail("expected SpeedTestException"); return
        } catch (e: SpeedTestException) {
            e
        }

        assertEquals(SpeedTestException.Reason.NO_PING_SAMPLES, e.reason)
        val state = c.state.value
        assertTrue("last state was $state", state is SpeedTestState.Error)
        assertEquals("ping", (state as SpeedTestState.Error).phase)
        assertTrue(state.throwable is SpeedTestException)
    }

    @Test
    fun `ping and probe work but every download chunk fails ends in Error download`() {
        // Probe ranges succeed, every other /download range returns 500.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.startsWith("/download/")) {
                    val range = request.getHeader("Range")
                    val size = when (range) {
                        "bytes=0-262143" -> 262144
                        "bytes=0-4194303" -> 4194304
                        else -> return MockResponse().setResponseCode(500)
                    }
                    return MockResponse().setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-${size - 1}/104857600")
                        .setBody(okio.Buffer().write(ByteArray(size)))
                }
                if (path.startsWith("/api/files")) return MockResponse().setResponseCode(500)
                return MockResponse().setResponseCode(200).setBody("OK")
            }
        }
        server.start()
        val c = client()

        val e = try {
            runBlocking { c.runFullTest() }
            fail("expected SpeedTestException"); return
        } catch (e: SpeedTestException) {
            e
        }

        assertEquals(SpeedTestException.Reason.NO_DATA_TRANSFERRED, e.reason)
        val state = c.state.value
        assertTrue("last state was $state", state is SpeedTestState.Error)
        assertEquals("download", (state as SpeedTestState.Error).phase)
    }

    @Test
    fun `sampler fed only zero bytes is never stable`() {
        val sampler = SpeedSampler()
        sampler.start()
        repeat(5) {
            Thread.sleep(20)
            sampler.sample()
        }
        assertEquals(0.0, sampler.peakMbps, 0.0)
        assertFalse(sampler.isStable(0))
        assertFalse(sampler.isStable(3000))
    }
}
