package com.speedtest.sdk

import com.speedtest.sdk.math.PingCalculator
import org.junit.Assert.*
import org.junit.Test

class PingCalculatorTest {

    // --- trimmedMean ---

    @Test
    fun `trimmedMean with known data 1 to 20`() {
        val samples = (1..20).toList()
        val result = PingCalculator.trimmedMean(samples, 0.1)
        // Drop 2 lowest (1,2) and 2 highest (19,20) → avg of 3..18
        assertEquals(10.5, result, 0.01)
    }

    @Test
    fun `trimmedMean with single element`() {
        assertEquals(42.0, PingCalculator.trimmedMean(listOf(42)), 0.01)
    }

    @Test
    fun `trimmedMean with two elements`() {
        assertEquals(5.0, PingCalculator.trimmedMean(listOf(3, 7)), 0.01)
    }

    @Test
    fun `trimmedMean with empty list`() {
        assertEquals(0.0, PingCalculator.trimmedMean(emptyList()), 0.01)
    }

    @Test
    fun `trimmedMean with odd count`() {
        val samples = (1..11).toList()
        val result = PingCalculator.trimmedMean(samples, 0.1)
        // Drop 1 lowest (1) and 1 highest (11) → avg of 2..10
        assertEquals(6.0, result, 0.01)
    }

    // --- median ---

    @Test
    fun `median with odd count`() {
        assertEquals(3.0, PingCalculator.median(listOf(1, 3, 5)), 0.01)
    }

    @Test
    fun `median with even count`() {
        assertEquals(2.5, PingCalculator.median(listOf(1, 2, 3, 4)), 0.01)
    }

    @Test
    fun `median with single element`() {
        assertEquals(7.0, PingCalculator.median(listOf(7)), 0.01)
    }

    @Test
    fun `median with unsorted input`() {
        assertEquals(3.0, PingCalculator.median(listOf(5, 1, 3)), 0.01)
    }

    // --- percentile ---

    @Test
    fun `p95 with 20 elements`() {
        val samples = (1..20).toList()
        val result = PingCalculator.percentile(samples, 0.95)
        // index = 0.95 * 19 = 18.05 → interpolate between samples[18]=19 and samples[19]=20
        assertEquals(19.05, result, 0.01)
    }

    @Test
    fun `p50 equals median`() {
        val samples = (1..10).toList()
        val p50 = PingCalculator.percentile(samples, 0.5)
        assertEquals(5.5, p50, 0.01)
    }

    @Test
    fun `p0 returns minimum`() {
        val samples = listOf(5, 10, 15, 20)
        assertEquals(5.0, PingCalculator.percentile(samples, 0.0), 0.01)
    }

    @Test
    fun `p100 returns maximum`() {
        val samples = listOf(5, 10, 15, 20)
        assertEquals(20.0, PingCalculator.percentile(samples, 1.0), 0.01)
    }

    // --- jitter ---

    @Test
    fun `jitter with constant samples is 0`() {
        assertEquals(0.0, PingCalculator.jitter(listOf(10, 10, 10, 10, 10)), 0.01)
    }

    @Test
    fun `jitter with alternating values`() {
        // Deltas: |20-10|=10, |10-20|=10, |20-10|=10 → median = 10
        assertEquals(10.0, PingCalculator.jitter(listOf(10, 20, 10, 20)), 0.01)
    }

    @Test
    fun `jitter with single sample is 0`() {
        assertEquals(0.0, PingCalculator.jitter(listOf(5)), 0.01)
    }

    @Test
    fun `jitter with two samples`() {
        assertEquals(5.0, PingCalculator.jitter(listOf(10, 15)), 0.01)
    }

    // --- isStable ---

    @Test
    fun `isStable returns true when within tolerance`() {
        val samples = listOf(100, 101, 99, 100, 100, 101, 99, 100)
        assertTrue(PingCalculator.isStable(samples, 100.0, 0.05))
    }

    @Test
    fun `isStable returns false when outside tolerance`() {
        val samples = listOf(100, 101, 99, 100, 100, 101, 99, 200)
        assertFalse(PingCalculator.isStable(samples, 50.0, 0.05))
    }

    @Test
    fun `isStable returns false with empty samples`() {
        assertFalse(PingCalculator.isStable(emptyList(), 100.0))
    }
}
