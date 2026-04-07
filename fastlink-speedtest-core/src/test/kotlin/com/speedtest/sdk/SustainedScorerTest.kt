package com.speedtest.sdk

import com.speedtest.sdk.math.SustainedScorer
import com.speedtest.sdk.model.SpeedSample
import org.junit.Assert.*
import org.junit.Test

class SustainedScorerTest {

    @Test
    fun `all samples at same speed returns that speed`() {
        // 20 samples at 50 Mbps, 200ms apart = 4 seconds
        val samples = (0 until 20).map { i ->
            SpeedSample(speedMbps = 50.0, timestampMs = 1000L + i * 200)
        }
        val result = SustainedScorer.score(samples, peakMbps = 50.0)
        assertEquals(50.0, result, 0.5)
    }

    @Test
    fun `brief spike is not selected`() {
        // 20 samples at 30 Mbps (4s) + 5 samples at 100 Mbps (1s) + 20 at 30 Mbps (4s)
        val base = (0 until 20).map { i ->
            SpeedSample(speedMbps = 30.0, timestampMs = 1000L + i * 200)
        }
        val spike = (0 until 5).map { i ->
            SpeedSample(speedMbps = 100.0, timestampMs = 5000L + i * 200)
        }
        val after = (0 until 20).map { i ->
            SpeedSample(speedMbps = 30.0, timestampMs = 6000L + i * 200)
        }
        val samples = base + spike + after
        val result = SustainedScorer.score(samples, peakMbps = 100.0)
        // Should pick 30 Mbps bucket, not the 100 Mbps spike
        assertTrue("Expected ~30 Mbps, got $result", result in 29.0..31.0)
    }

    @Test
    fun `empty samples returns 0`() {
        assertEquals(0.0, SustainedScorer.score(emptyList(), peakMbps = 0.0), 0.01)
    }

    @Test
    fun `single sample returns clamped to peak`() {
        val samples = listOf(SpeedSample(speedMbps = 50.0, timestampMs = 1000))
        val result = SustainedScorer.score(samples, peakMbps = 40.0)
        assertEquals(40.0, result, 0.01)
    }

    @Test
    fun `peak clamping works`() {
        // All samples at 100 Mbps but peak is 80
        val samples = (0 until 20).map { i ->
            SpeedSample(speedMbps = 100.0, timestampMs = 1000L + i * 200)
        }
        val result = SustainedScorer.score(samples, peakMbps = 80.0)
        assertTrue("Result $result should be <= 80", result <= 80.0)
    }

    @Test
    fun `fallback to peak when no bucket qualifies`() {
        // Only 2 samples (not enough for 3 seconds)
        val samples = listOf(
            SpeedSample(speedMbps = 50.0, timestampMs = 1000),
            SpeedSample(speedMbps = 50.0, timestampMs = 1200),
        )
        val result = SustainedScorer.score(samples, peakMbps = 55.0)
        // Only 200ms in the bucket, so falls back to peak
        assertEquals(55.0, result, 0.01)
    }
}
