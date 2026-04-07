package com.speedtest.sdk.util

import android.util.Log

/**
 * Internal SDK logger. All debug logs use the tag "SpeedTestSDK".
 */
internal object SdkLogger {
    private const val TAG = "SpeedTestSDK"

    fun d(subtag: String, message: String) {
        Log.d(TAG, "[$subtag] $message")
    }

    fun w(subtag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(TAG, "[$subtag] $message", throwable)
        } else {
            Log.w(TAG, "[$subtag] $message")
        }
    }

    fun e(subtag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[$subtag] $message", throwable)
        } else {
            Log.e(TAG, "[$subtag] $message")
        }
    }
}
