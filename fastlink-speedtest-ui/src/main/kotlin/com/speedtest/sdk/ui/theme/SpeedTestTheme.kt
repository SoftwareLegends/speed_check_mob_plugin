package com.speedtest.sdk.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern color and dimension tokens for the SpeedTest UI.
 *
 * Design language: dark glassmorphism with neon accent gradients,
 * generous spacing, and bold display typography.
 */
object SpeedTestTheme {
    // ─────────────────────────────────────────────────────────
    // Accent palette — neon gradients
    // ─────────────────────────────────────────────────────────
    val DownloadColor = Color(0xFF00E5FF)            // Cyan
    val DownloadColorEnd = Color(0xFF536DFE)         // Indigo
    val UploadColor = Color(0xFFFF4081)              // Pink
    val UploadColorEnd = Color(0xFFE040FB)           // Magenta
    val PingColor = Color(0xFF00E676)                // Lime
    val PingColorEnd = Color(0xFF76FF03)             // Green
    val ErrorColor = Color(0xFFFF5252)
    val ErrorColorEnd = Color(0xFFFF1744)

    val DownloadGradient = Brush.linearGradient(listOf(DownloadColor, DownloadColorEnd))
    val UploadGradient = Brush.linearGradient(listOf(UploadColor, UploadColorEnd))
    val PingGradient = Brush.linearGradient(listOf(PingColor, PingColorEnd))
    val ErrorGradient = Brush.linearGradient(listOf(ErrorColor, ErrorColorEnd))

    // ─────────────────────────────────────────────────────────
    // Background — deep space gradient mesh
    // ─────────────────────────────────────────────────────────
    val BackgroundDark = Color(0xFF06070D)
    val BackgroundMid = Color(0xFF0B1023)
    val BackgroundTop = Color(0xFF131A36)

    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(BackgroundTop, BackgroundMid, BackgroundDark),
    )

    // ─────────────────────────────────────────────────────────
    // Glass surfaces
    // ─────────────────────────────────────────────────────────
    val SurfaceDark = Color(0xFF131A36)
    val GlassFill = Color(0x14FFFFFF)                // 8% white
    val GlassStroke = Color(0x33FFFFFF)              // 20% white
    val GlassFillStrong = Color(0x1FFFFFFF)          // 12% white

    // Gauge
    val GaugeTrack = Color(0x1AFFFFFF)               // 10% white
    val GaugeNeedle = Color(0xFFFFFFFF)

    // Text
    val OnSurfaceLight = Color(0xFFF5F7FF)
    val OnSurfaceMid = Color(0xFFB8BFD9)
    val OnSurfaceDim = Color(0xFF7A819E)

    // ─────────────────────────────────────────────────────────
    // Dimensions
    // ─────────────────────────────────────────────────────────
    val GaugeSize = 300.dp
    val GaugeStrokeWidth = 18.dp
    val SpeedTextSize = 64.sp
    val UnitTextSize = 14.sp
    val LabelTextSize = 12.sp

    // Gauge arc configuration (3/4 ring opening at the bottom)
    const val GaugeStartAngle = 135f
    const val GaugeSweepAngle = 270f
}
