package com.speedtest.sdk.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedtest.sdk.ui.theme.SpeedTestTheme
import kotlin.math.*

/**
 * Modern animated speed gauge.
 *
 * Visual treatment:
 * - 270° glass ring track with rounded caps
 * - Active arc filled with a sweep gradient using the accent color
 * - Soft outer glow halo behind the active arc
 * - Inner shimmer ring that subtly rotates while active
 * - Large display number with `Mbps` unit, plus contextual label
 *
 * Logarithmic mapping covers 0.1 → 1000 Mbps without crowding the low end.
 */
@Composable
fun SpeedGauge(
    currentMbps: Double,
    accentColor: Color,
    modifier: Modifier = Modifier,
    maxMbps: Double = 1000.0,
    label: String = "Mbps",
) {
    val targetFraction = speedToFraction(currentMbps, maxMbps).toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "gaugeFraction",
    )

    // Subtle infinite rotation for the shimmer ring
    val infinite = rememberInfiniteTransition(label = "gaugeShimmer")
    val shimmerSweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerSweep",
    )

    val accentGradient = remember(accentColor) {
        Brush.sweepGradient(
            listOf(
                accentColor.copy(alpha = 0.0f),
                accentColor.copy(alpha = 0.4f),
                accentColor,
                accentColor.copy(alpha = 0.4f),
                accentColor.copy(alpha = 0.0f),
            ),
        )
    }

    Box(
        modifier = modifier.size(SpeedTestTheme.GaugeSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            val strokeWidth = SpeedTestTheme.GaugeStrokeWidth.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Outer soft glow halo (drawn beneath, multiple passes for bloom)
            for (i in 0 until 4) {
                val haloAlpha = 0.06f - i * 0.012f
                val haloWidth = strokeWidth * (1.6f + i * 0.6f)
                drawArc(
                    color = accentColor.copy(alpha = haloAlpha),
                    startAngle = SpeedTestTheme.GaugeStartAngle,
                    sweepAngle = SpeedTestTheme.GaugeSweepAngle * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = haloWidth, cap = StrokeCap.Round),
                )
            }

            // Glass track
            drawArc(
                color = SpeedTestTheme.GaugeTrack,
                startAngle = SpeedTestTheme.GaugeStartAngle,
                sweepAngle = SpeedTestTheme.GaugeSweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Active arc with sweep gradient
            drawArc(
                brush = accentGradient,
                startAngle = SpeedTestTheme.GaugeStartAngle,
                sweepAngle = SpeedTestTheme.GaugeSweepAngle * animatedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Inner shimmer dotted ticks (decorative scale markers)
            val center = Offset(size.width / 2, size.height / 2)
            val radius = (size.width - strokeWidth) / 2
            val tickValues = listOf(1.0, 5.0, 25.0, 100.0, 500.0)
            for (tickValue in tickValues) {
                val tickFraction = speedToFraction(tickValue, maxMbps)
                val angle = Math.toRadians(
                    (SpeedTestTheme.GaugeStartAngle +
                        SpeedTestTheme.GaugeSweepAngle * tickFraction).toDouble(),
                )
                val tickInner = radius - strokeWidth * 1.6f
                val pt = Offset(
                    center.x + tickInner * cos(angle).toFloat(),
                    center.y + tickInner * sin(angle).toFloat(),
                )
                val isActive = tickFraction <= animatedFraction
                drawCircle(
                    color = if (isActive) accentColor else SpeedTestTheme.OnSurfaceDim.copy(alpha = 0.5f),
                    radius = if (isActive) 3.5f else 2f,
                    center = pt,
                )
            }

            // Animated rotating shimmer dot riding the active arc edge
            if (animatedFraction > 0.01f) {
                val edgeAngle = Math.toRadians(
                    (SpeedTestTheme.GaugeStartAngle +
                        SpeedTestTheme.GaugeSweepAngle * animatedFraction).toDouble(),
                )
                val edgeRadius = radius
                val edgePt = Offset(
                    center.x + edgeRadius * cos(edgeAngle).toFloat(),
                    center.y + edgeRadius * sin(edgeAngle).toFloat(),
                )
                // Soft glow around the head
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f),
                    radius = strokeWidth * 0.9f,
                    center = edgePt,
                )
                drawCircle(
                    color = Color.White,
                    radius = strokeWidth * 0.32f,
                    center = edgePt,
                )
            }

            // Subtle rotating decorative ring (very faint)
            drawArc(
                color = Color.White.copy(alpha = 0.04f),
                startAngle = shimmerSweep,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(strokeWidth * 1.5f, strokeWidth * 1.5f),
                size = Size(
                    size.width - strokeWidth * 3f,
                    size.height - strokeWidth * 3f,
                ),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round),
            )
        }

        // Center display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = SpeedTestTheme.OnSurfaceDim,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatSpeed(currentMbps),
                fontSize = SpeedTestTheme.SpeedTextSize,
                fontWeight = FontWeight.Black,
                color = SpeedTestTheme.OnSurfaceLight,
            )
            Text(
                text = "Mbps",
                fontSize = SpeedTestTheme.UnitTextSize,
                color = SpeedTestTheme.OnSurfaceMid,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }
    }
}

/** Logarithmic mapping of speed to [0..1] fraction. */
private fun speedToFraction(mbps: Double, maxMbps: Double): Double {
    if (mbps <= 0.0) return 0.0
    val logMin = ln(0.1)
    val logMax = ln(maxMbps)
    val logValue = ln(mbps.coerceIn(0.1, maxMbps))
    return ((logValue - logMin) / (logMax - logMin)).coerceIn(0.0, 1.0)
}

private fun formatSpeed(mbps: Double): String = when {
    mbps < 1.0 -> "%.2f".format(mbps)
    mbps < 10.0 -> "%.1f".format(mbps)
    else -> "%.0f".format(mbps)
}
