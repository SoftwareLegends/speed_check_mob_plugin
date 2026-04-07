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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedtest.sdk.ui.theme.SpeedTestTheme

/**
 * Real-time ping/jitter display rendered as glass pill chips.
 */
@Composable
fun PingDisplay(
    pingMs: Int,
    jitterMs: Double = 0.0,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        GlassPill(
            label = "PING",
            value = "${pingMs} ms",
            accent = SpeedTestTheme.PingColor,
        )
        if (jitterMs > 0.0) {
            Spacer(Modifier.width(12.dp))
            GlassPill(
                label = "JITTER",
                value = "%.1f ms".format(jitterMs),
                accent = SpeedTestTheme.PingColor,
            )
        }
    }
}

@Composable
private fun GlassPill(label: String, value: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SpeedTestTheme.GlassFill)
            .border(
                width = 1.dp,
                color = SpeedTestTheme.GlassStroke,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = SpeedTestTheme.OnSurfaceDim,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = SpeedTestTheme.OnSurfaceLight,
        )
    }
}
