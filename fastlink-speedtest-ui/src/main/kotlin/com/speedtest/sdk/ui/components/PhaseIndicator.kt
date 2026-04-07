package com.speedtest.sdk.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedtest.sdk.ui.theme.SpeedTestTheme

/**
 * Modern phase indicator with three bouncing dots and a label.
 *
 * Each dot has a staggered vertical bounce and breathing alpha,
 * giving a lively rhythm during transition phases (Connecting, Probing, …).
 */
@Composable
fun PhaseIndicator(
    phaseName: String,
    modifier: Modifier = Modifier,
    color: Color = SpeedTestTheme.DownloadColor,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BouncingDots(color = color)
        Spacer(Modifier.height(20.dp))
        Text(
            text = phaseName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = SpeedTestTheme.OnSurfaceLight,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun BouncingDots(color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { index ->
            BounceDot(color = color, delayMillis = index * 160)
        }
    }
}

@Composable
private fun BounceDot(color: Color, delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "bounce$delayMillis")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 0
                1f at 250
                0f at 500
                0f at 1000
            },
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "offset",
    )
    val scale = 0.7f + offset * 0.6f
    val alpha = 0.4f + offset * 0.6f
    Box(
        modifier = Modifier
            .size((10 * scale).dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}
