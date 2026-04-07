package com.speedtest.sdk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.SpeedTestState
import com.speedtest.sdk.ui.components.*
import com.speedtest.sdk.ui.theme.SpeedTestTheme

/**
 * Full-screen speed test composable.
 *
 * Features a layered mesh-gradient background, glassmorphic surfaces,
 * neon accent gradients per phase, and bouncy spring transitions
 * between states.
 *
 * @param config Speed test server configuration
 * @param onFinished Optional callback when the test completes
 * @param viewModel Injected ViewModel (provided by Hilt)
 */
@Composable
fun SpeedTestScreen(
    config: SpeedTestConfig,
    onFinished: ((SpeedTestResult) -> Unit)? = null,
    viewModel: SpeedTestViewModel = hiltViewModel(),
) {
    LaunchedEffect(config) { viewModel.configure(config) }

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is SpeedTestState.Finished) {
            onFinished?.invoke((state as SpeedTestState.Finished).result)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeedTestTheme.BackgroundGradient),
    ) {
        // Animated mesh-gradient blobs behind everything
        MeshGradientBackdrop(currentState = state)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeaderBar(serverUrl = config.baseUrl, state = state)
            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = state::class,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(400)) togetherWith
                        fadeOut(animationSpec = tween(200)))
                },
                label = "stateSwitch",
            ) { _ ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StateContent(
                        state = state,
                        onStart = { viewModel.startTest() },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            BottomActionBar(state = state, onAction = { viewModel.startTest() })
        }
    }
}

/* ───────────────────────── State content ───────────────────────── */

@Composable
private fun StateContent(state: SpeedTestState, onStart: () -> Unit) {
    when (state) {
        is SpeedTestState.Idle -> IdleContent(onStart = onStart)

        is SpeedTestState.Connecting ->
            PhaseIndicator(phaseName = "Connecting to server")

        is SpeedTestState.MeasuringPing -> {
            PhaseIndicator(
                phaseName = "Measuring latency",
                color = SpeedTestTheme.PingColor,
            )
            Spacer(Modifier.height(28.dp))
            PingDisplay(pingMs = state.currentMedian)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${state.samples.size} samples",
                fontSize = 12.sp,
                color = SpeedTestTheme.OnSurfaceDim,
            )
        }

        is SpeedTestState.Probing ->
            PhaseIndicator(phaseName = "Probing connection")

        is SpeedTestState.Downloading -> {
            SpeedGauge(
                currentMbps = state.currentMbps,
                accentColor = SpeedTestTheme.DownloadColor,
                label = "Download",
            )
            Spacer(Modifier.height(20.dp))
            PhaseProgress(
                progress = state.progress,
                color = SpeedTestTheme.DownloadColor,
            )
            Spacer(Modifier.height(20.dp))
            BentoMiniStats(
                peak = state.peakMbps,
                connections = state.connections,
                elapsedMs = state.elapsedMs,
                accent = SpeedTestTheme.DownloadColor,
            )
        }

        is SpeedTestState.Uploading -> {
            SpeedGauge(
                currentMbps = state.currentMbps,
                accentColor = SpeedTestTheme.UploadColor,
                label = "Upload",
            )
            Spacer(Modifier.height(20.dp))
            PhaseProgress(
                progress = state.progress,
                color = SpeedTestTheme.UploadColor,
            )
            Spacer(Modifier.height(20.dp))
            BentoMiniStats(
                peak = state.peakMbps,
                connections = state.connections,
                elapsedMs = state.elapsedMs,
                accent = SpeedTestTheme.UploadColor,
            )
        }

        is SpeedTestState.Finished -> ResultSummary(result = state.result)

        is SpeedTestState.Error -> ErrorContent(state = state)
    }
}

/* ───────────────────────── Idle / Hero ───────────────────────── */

@Composable
private fun IdleContent(onStart: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "idlePulse")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Concentric glass rings hint at the gauge
        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(CircleShape)
                    .background(SpeedTestTheme.GlassFill)
                    .border(1.dp, SpeedTestTheme.GlassStroke, CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(SpeedTestTheme.GlassFillStrong)
                    .border(1.dp, SpeedTestTheme.GlassStroke, CircleShape),
            )
            Box(
                modifier = Modifier
                    .size((150 * scale).dp)
                    .clip(CircleShape)
                    .background(SpeedTestTheme.DownloadGradient)
                    .clickable { onStart() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GO",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 4.sp,
                    )
                    Text(
                        text = "Tap to start",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Check your internet performance",
            fontSize = 14.sp,
            color = SpeedTestTheme.OnSurfaceMid,
        )
    }
}

/* ───────────────────────── Header / Bottom ───────────────────────── */

@Composable
private fun HeaderBar(serverUrl: String, state: SpeedTestState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SPEED TEST",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = SpeedTestTheme.OnSurfaceDim,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = phaseTitle(state),
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = SpeedTestTheme.OnSurfaceLight,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SpeedTestTheme.GlassFill)
                .border(1.dp, SpeedTestTheme.GlassStroke, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(SpeedTestTheme.PingColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = serverUrl,
                fontSize = 11.sp,
                color = SpeedTestTheme.OnSurfaceMid,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun BottomActionBar(state: SpeedTestState, onAction: () -> Unit) {
    when (state) {
        is SpeedTestState.Finished -> GradientCta(
            text = "Test Again",
            gradient = SpeedTestTheme.DownloadGradient,
            onClick = onAction,
        )
        is SpeedTestState.Error -> GradientCta(
            text = "Retry",
            gradient = SpeedTestTheme.ErrorGradient,
            onClick = onAction,
        )
        else -> Spacer(Modifier.height(56.dp))
    }
}

@Composable
private fun GradientCta(text: String, gradient: Brush, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            letterSpacing = 1.sp,
        )
    }
}

/* ───────────────────────── Stats / Progress ───────────────────────── */

@Composable
private fun BentoMiniStats(
    peak: Double,
    connections: Int,
    elapsedMs: Long,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniStatCard(
            label = "PEAK",
            value = "%.1f".format(peak),
            unit = "Mbps",
            accent = accent,
            modifier = Modifier.weight(1f),
        )
        MiniStatCard(
            label = "CONNS",
            value = "$connections",
            unit = "active",
            accent = accent,
            modifier = Modifier.weight(1f),
        )
        MiniStatCard(
            label = "TIME",
            value = "%.1f".format(elapsedMs / 1000.0),
            unit = "sec",
            accent = accent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SpeedTestTheme.GlassFill)
            .border(1.dp, SpeedTestTheme.GlassStroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = SpeedTestTheme.OnSurfaceDim,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            color = SpeedTestTheme.OnSurfaceLight,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = unit,
            fontSize = 10.sp,
            color = accent.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Slim, animated phase progress bar — no label, just the rail. */
@Composable
private fun PhaseProgress(progress: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "phaseProgress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(SpeedTestTheme.GaugeTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.6f), color),
                    ),
                ),
        )
    }
}

/* ───────────────────────── Error ───────────────────────── */

@Composable
private fun ErrorContent(state: SpeedTestState.Error) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SpeedTestTheme.GlassFill)
            .border(1.dp, SpeedTestTheme.ErrorColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SpeedTestTheme.ErrorColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = SpeedTestTheme.ErrorColor,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Error during ${state.phase}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = SpeedTestTheme.OnSurfaceLight,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.throwable.message ?: "Unknown error",
            fontSize = 13.sp,
            color = SpeedTestTheme.OnSurfaceMid,
            textAlign = TextAlign.Center,
        )
    }
}

/* ───────────────────────── Mesh backdrop ───────────────────────── */

@Composable
private fun MeshGradientBackdrop(currentState: SpeedTestState) {
    val transition = rememberInfiniteTransition(label = "mesh")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "meshT",
    )

    val accent = when (currentState) {
        is SpeedTestState.Downloading -> SpeedTestTheme.DownloadColor
        is SpeedTestState.Uploading -> SpeedTestTheme.UploadColor
        is SpeedTestState.MeasuringPing -> SpeedTestTheme.PingColor
        is SpeedTestState.Error -> SpeedTestTheme.ErrorColor
        else -> SpeedTestTheme.DownloadColor
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Two big radial blobs that breathe
        val c1 = Offset(w * (0.25f + 0.1f * t), h * (0.2f + 0.05f * t))
        val c2 = Offset(w * (0.8f - 0.1f * t), h * (0.75f - 0.05f * t))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.25f), Color.Transparent),
                center = c1,
                radius = w * 0.7f,
            ),
            radius = w * 0.7f,
            center = c1,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SpeedTestTheme.UploadColor.copy(alpha = 0.18f),
                    Color.Transparent,
                ),
                center = c2,
                radius = w * 0.7f,
            ),
            radius = w * 0.7f,
            center = c2,
        )
    }
}

/* ───────────────────────── Helpers ───────────────────────── */

private fun phaseTitle(state: SpeedTestState): String = when (state) {
    is SpeedTestState.Idle -> "Ready to test"
    is SpeedTestState.Connecting -> "Connecting"
    is SpeedTestState.MeasuringPing -> "Latency"
    is SpeedTestState.Probing -> "Probing"
    is SpeedTestState.Downloading -> "Download"
    is SpeedTestState.Uploading -> "Upload"
    is SpeedTestState.Finished -> "Results"
    is SpeedTestState.Error -> "Failed"
}
