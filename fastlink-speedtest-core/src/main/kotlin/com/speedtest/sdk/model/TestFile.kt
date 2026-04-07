package com.speedtest.sdk.model

import kotlinx.serialization.Serializable

/**
 * A test file available on the speed test server.
 *
 * @param name Filename (e.g., "test-1MB.bin")
 * @param size File size in bytes
 * @param label Human-readable label (e.g., "1 MB")
 */
@Serializable
data class TestFile(
    val name: String,
    val size: Long,
    val label: String,
)
