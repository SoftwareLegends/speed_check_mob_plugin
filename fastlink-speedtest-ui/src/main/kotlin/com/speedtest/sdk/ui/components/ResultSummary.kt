package com.speedtest.sdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.ui.theme.SpeedTestTheme

/**
 * Final result card with a glassmorphic surface and a bento-style stat grid.
 *
 * Layout:
 *   ┌─────────────────────────────┐
 *   │  ✓  Test complete           │
 *   ├──────────────┬──────────────┤
 *   │  ↓ Download  │  ↑ Upload    │
 *   ├──────┬───────┴──────┬───────┤
 *   │ Ping │   Jitter     │ Loss  │
 *   └──────┴──────────────┴───────┘
 */
@Composable
fun ResultSummary(
    result: SpeedTestResult,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SpeedTestTheme.GlassFill)
            .border(
                width = 1.dp,
                color = SpeedTestTheme.GlassStroke,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(24.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SpeedTestTheme.PingColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = SpeedTestTheme.PingColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Test Complete",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpeedTestTheme.OnSurfaceLight,
                )
                Text(
                    text = "Tap below to run again",
                    fontSize = 12.sp,
                    color = SpeedTestTheme.OnSurfaceDim,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Bento row 1: Download / Upload
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BentoSpeedTile(
                modifier = Modifier.weight(1f),
                icon = "↓",
                label = "Download",
                speedMbps = result.download.finalMbps,
                gradient = SpeedTestTheme.DownloadGradient,
                accent = SpeedTestTheme.DownloadColor,
            )
            BentoSpeedTile(
                modifier = Modifier.weight(1f),
                icon = "↑",
                label = "Upload",
                speedMbps = result.upload.finalMbps,
                gradient = SpeedTestTheme.UploadGradient,
                accent = SpeedTestTheme.UploadColor,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Bento row 2: Ping / Jitter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BentoStatTile(
                modifier = Modifier.weight(1f),
                label = "Ping",
                value = "%.0f".format(result.ping.avg),
                unit = "ms",
                accent = SpeedTestTheme.PingColor,
            )
            BentoStatTile(
                modifier = Modifier.weight(1f),
                label = "Jitter",
                value = "%.1f".format(result.ping.jitter),
                unit = "ms",
                accent = SpeedTestTheme.PingColor,
            )
        }
    }
}

@Composable
private fun BentoSpeedTile(
    modifier: Modifier,
    icon: String,
    label: String,
    speedMbps: Double,
    gradient: Brush,
    accent: Color,
    cornerRadius: Dp = 20.dp,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(SpeedTestTheme.GlassFillStrong)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(cornerRadius),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(gradient),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = icon,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                color = SpeedTestTheme.OnSurfaceDim,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatResultSpeed(speedMbps),
                fontSize = 32.sp,
                color = SpeedTestTheme.OnSurfaceLight,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Mbps",
                fontSize = 12.sp,
                color = SpeedTestTheme.OnSurfaceMid,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun BentoStatTile(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    accent: Color,
    cornerRadius: Dp = 20.dp,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(SpeedTestTheme.GlassFillStrong)
            .border(
                width = 1.dp,
                color = SpeedTestTheme.GlassStroke,
                shape = RoundedCornerShape(cornerRadius),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                color = SpeedTestTheme.OnSurfaceDim,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 24.sp,
                color = SpeedTestTheme.OnSurfaceLight,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                fontSize = 11.sp,
                color = SpeedTestTheme.OnSurfaceMid,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

private fun formatResultSpeed(mbps: Double): String = when {
    mbps < 1.0 -> "%.2f".format(mbps)
    mbps < 10.0 -> "%.1f".format(mbps)
    else -> "%.0f".format(mbps)
}
