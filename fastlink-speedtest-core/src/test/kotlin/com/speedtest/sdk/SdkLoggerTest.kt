package com.speedtest.sdk

import com.speedtest.sdk.util.LogSink
import com.speedtest.sdk.util.SdkLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class SdkLoggerTest {
    @Test
    fun `logger routes to injected sink without android Log`() {
        val lines = mutableListOf<String>()
        SdkLogger.sink = object : LogSink {
            override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
                lines += "$tag:$message"
            }
        }
        SdkLogger.d("x", "hello")
        SdkLogger.e("x", "bad", RuntimeException("boom"))
        assertEquals(listOf("SpeedTestSDK:[x] hello", "SpeedTestSDK:[x] bad"), lines)
    }
}
