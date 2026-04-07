# Keep public API
-keep class com.speedtest.sdk.SpeedTestClient { *; }
-keep class com.speedtest.sdk.SpeedTestConfig { *; }
-keep class com.speedtest.sdk.SpeedTestState { *; }
-keep class com.speedtest.sdk.SpeedTestState$* { *; }
-keep class com.speedtest.sdk.SpeedTestResult { *; }
-keep class com.speedtest.sdk.model.PingStats { *; }
-keep class com.speedtest.sdk.model.PhaseResult { *; }

# Keep serialization
-keepclassmembers class com.speedtest.sdk.model.TestFile { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
