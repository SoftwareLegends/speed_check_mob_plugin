package com.speedtest.sdk.util

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe cache of random byte arrays keyed by size.
 * Reuses payloads to avoid repeated allocation during upload tests.
 */
object PayloadCache {
    private val cache = ConcurrentHashMap<Int, ByteArray>()

    /**
     * Get a random byte array of the given [size].
     * Creates and caches a new one if not already cached.
     */
    fun get(size: Int): ByteArray = cache.getOrPut(size) {
        ByteArray(size).also { SecureRandom().nextBytes(it) }
    }

    /** Clear all cached payloads. */
    fun clear() = cache.clear()
}
