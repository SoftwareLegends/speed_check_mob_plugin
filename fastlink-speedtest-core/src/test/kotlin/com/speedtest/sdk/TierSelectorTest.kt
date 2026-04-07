package com.speedtest.sdk

import com.speedtest.sdk.model.Tier
import com.speedtest.sdk.model.TierSelector
import org.junit.Assert.*
import org.junit.Test

class TierSelectorTest {

    @Test
    fun `pickTier 0_5 returns first tier`() {
        val tier = TierSelector.pickTier(0.5)
        assertEquals(0.8, tier.maxMbps, 0.01)
    }

    @Test
    fun `pickTier 5 returns tier with maxMbps 12`() {
        val tier = TierSelector.pickTier(5.0)
        assertEquals(12.0, tier.maxMbps, 0.01)
    }

    @Test
    fun `pickTier 999 returns tier with maxMbps 1000`() {
        val tier = TierSelector.pickTier(999.0)
        assertEquals(1000.0, tier.maxMbps, 0.01)
    }

    @Test
    fun `pickTier 2000 returns last tier`() {
        val tier = TierSelector.pickTier(2000.0)
        assertEquals(Double.MAX_VALUE, tier.maxMbps, 0.01)
        assertEquals(12, tier.conn)
        assertEquals(20, tier.maxConn)
    }

    @Test
    fun `pickTier 0 returns first tier`() {
        val tier = TierSelector.pickTier(0.0)
        assertEquals(0.8, tier.maxMbps, 0.01)
        assertEquals(1, tier.conn)
    }

    @Test
    fun `pickTier boundary value 0_8 returns first tier`() {
        val tier = TierSelector.pickTier(0.8)
        assertEquals(0.8, tier.maxMbps, 0.01)
    }

    @Test
    fun `pickTier just above boundary returns next tier`() {
        val tier = TierSelector.pickTier(0.81)
        assertEquals(3.0, tier.maxMbps, 0.01)
    }

    @Test
    fun `tier table has 7 entries`() {
        assertEquals(7, Tier.TABLE.size)
    }

    @Test
    fun `tier files are correct for each tier`() {
        assertEquals(listOf("test-1MB.bin", "test-5MB.bin"), Tier.TABLE[0].files)
        assertEquals(listOf("test-500MB.bin", "test-1024MB.bin"), Tier.TABLE.last().files)
    }
}
