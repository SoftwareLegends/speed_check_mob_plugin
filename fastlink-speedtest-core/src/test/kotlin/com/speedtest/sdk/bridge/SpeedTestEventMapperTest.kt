package com.speedtest.sdk.bridge

import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.SpeedTestState
import com.speedtest.sdk.model.PhaseResult
import com.speedtest.sdk.model.PingStats
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class SpeedTestEventMapperTest {
    private val result = SpeedTestResult(
        ping = PingStats(12.5, 12.0, 20.0, 1.5, 10, 30, listOf(10, 12, 30)),
        download = PhaseResult(90.0, 100.0, 85.0, 10_000),
        upload = PhaseResult(40.0, 45.0, 38.0, 10_000),
        serverUrl = "https://user:secret@speed.example.com",
        timestamp = 1L,
    )

    @Test fun idleConnectingProbe() {
        assertEquals(mapOf("phase" to "idle"), SpeedTestEventMapper.toEvent(SpeedTestState.Idle))
        assertEquals(mapOf("phase" to "connecting"), SpeedTestEventMapper.toEvent(SpeedTestState.Connecting))
        assertEquals(mapOf("phase" to "probe"), SpeedTestEventMapper.toEvent(SpeedTestState.Probing))
    }

    @Test fun ping() {
        val e = SpeedTestEventMapper.toEvent(SpeedTestState.MeasuringPing(listOf(10, 14), 12))
        assertEquals("ping", e["phase"]); assertEquals(12.0, e["pingMs"])
    }

    @Test fun downloadAndUpload() {
        val d = SpeedTestEventMapper.toEvent(SpeedTestState.Downloading(50.0, 60.0, 1000, 4, 0.5f))
        assertEquals(setOf("phase", "currentMbps", "peakMbps", "progress"), d.keys)
        assertEquals("download", d["phase"]); assertEquals(0.5, d["progress"])
        val u = SpeedTestEventMapper.toEvent(SpeedTestState.Uploading(20.0, 25.0, 1000, 2, 1.5f))
        assertEquals("upload", u["phase"]); assertEquals(1.0, u["progress"])
    }

    @Test fun finishedHasResultWithoutCredentials() {
        val e = SpeedTestEventMapper.toEvent(SpeedTestState.Finished(result))
        assertEquals("finished", e["phase"])
        @Suppress("UNCHECKED_CAST") val r = e["result"] as Map<String, Any?>
        assertEquals(90.0, r["download"]); assertEquals(40.0, r["upload"])
        assertEquals(12.5, r["ping"]); assertEquals(1.5, r["jitter"])
        assertFalse(r["serverUrl"].toString().contains("secret"))
    }

    @Test fun errorMapsCode() {
        val e = SpeedTestEventMapper.toEvent(SpeedTestState.Error("download", IOException("boom https://u:pw@h/x")))
        assertEquals("error", e["phase"])
        @Suppress("UNCHECKED_CAST") val err = e["error"] as Map<String, Any?>
        assertEquals("download", err["phase"]); assertEquals("NETWORK", err["code"])
        assertFalse(err["message"].toString().contains("pw"))
        assertEquals("TIMEOUT", SpeedTestEventMapper.codeFor(SocketTimeoutException()))
        assertEquals("UNKNOWN", SpeedTestEventMapper.codeFor(IllegalStateException()))
        assertEquals("CANCELLED", SpeedTestEventMapper.codeFor(kotlinx.coroutines.CancellationException("x")))
        assertTrue(SpeedTestEventMapper.cancelledEvent()["phase"] == "cancelled")
    }
}
