package com.speedtest.sdk.model

/**
 * Selects the appropriate [Tier] based on a probed speed.
 */
object TierSelector {

    /**
     * Returns the first tier where [probeMbps] <= tier.maxMbps.
     * If probeMbps exceeds all thresholds, returns the last (highest) tier.
     */
    fun pickTier(probeMbps: Double): Tier {
        return Tier.TABLE.firstOrNull { probeMbps <= it.maxMbps }
            ?: Tier.TABLE.last()
    }
}
