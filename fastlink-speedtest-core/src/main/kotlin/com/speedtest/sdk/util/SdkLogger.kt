package com.speedtest.sdk.util

import android.util.Log

/** Destination for SDK log lines. Replaceable so JVM unit tests don't need android.util.Log. */
internal interface LogSink {
    fun log(priority: Int, tag: String, message: String, throwable: Throwable?)
}

internal object AndroidLogSink : LogSink {
    override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.println(priority, tag, message + "\n" + Log.getStackTraceString(throwable))
        } else {
            Log.println(priority, tag, message)
        }
    }
}

/** Prints to stdout; used as the default in JVM unit tests. */
internal object PrintlnLogSink : LogSink {
    override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        println("$priority/$tag: $message" + (throwable?.let { " ($it)" } ?: ""))
    }
}

/**
 * Internal SDK logger. All debug logs use the tag "SpeedTestSDK".
 */
internal object SdkLogger {
    private const val TAG = "SpeedTestSDK"

    @Volatile
    internal var sink: LogSink = AndroidLogSink

    fun d(subtag: String, message: String) {
        sink.log(Log.DEBUG, TAG, "[$subtag] $message", null)
    }

    fun w(subtag: String, message: String, throwable: Throwable? = null) {
        sink.log(Log.WARN, TAG, "[$subtag] $message", throwable)
    }

    fun e(subtag: String, message: String, throwable: Throwable? = null) {
        sink.log(Log.ERROR, TAG, "[$subtag] $message", throwable)
    }
}
