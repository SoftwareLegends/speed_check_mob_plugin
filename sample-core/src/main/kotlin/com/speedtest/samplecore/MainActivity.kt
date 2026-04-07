package com.speedtest.samplecore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speedtest.sdk.SpeedTestClient
import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.SpeedTestState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Demonstrates direct integration with `speedtest-core` using Jetpack Compose
 * — without depending on the `speedtest-ui` module.
 *
 * Build your own UI on top of [SpeedTestClient.state] (a [kotlinx.coroutines.flow.StateFlow]).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color(0xFF121220)) {
                    SpeedTestCoreSample()
                }
            }
        }
    }
}

@Composable
private fun SpeedTestCoreSample() {
    var serverUrl by rememberSaveable { mutableStateOf("https://speed.sl8.org") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Hold the client across recompositions; rebuilt when config changes.
    var client by remember { mutableStateOf<SpeedTestClient?>(null) }

    // Empty fallback when no client exists yet.
    val emptyState = remember { MutableStateFlow<SpeedTestState>(SpeedTestState.Idle) }
    val state by (client?.state ?: emptyState).collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "SpeedTest Core (Compose, no UI module)",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        ConfigField(value = serverUrl, label = "Server URL", onValueChange = { serverUrl = it })
        ConfigField(value = username, label = "Username (Basic Auth)", onValueChange = { username = it })
        ConfigField(
            value = password,
            label = "Password (Basic Auth)",
            onValueChange = { password = it },
            isPassword = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    val newClient = SpeedTestClient(
                        SpeedTestConfig(
                            baseUrl = serverUrl.trim(),
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                        )
                    )
                    client = newClient
                    running = true
                    scope.launch {
                        try {
                            newClient.runFullTest()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Error already emitted via state
                        } finally {
                            running = false
                        }
                    }
                },
                enabled = !running,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Start Test", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    client?.cancel()
                    running = false
                },
                enabled = running,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Cancel")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Render the live state using Compose primitives.
        StateView(state)
    }
}

@Composable
private fun ConfigField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF00BCD4),
            unfocusedBorderColor = Color.Gray,
            focusedLabelColor = Color(0xFF00BCD4),
            unfocusedLabelColor = Color.Gray,
        ),
    )
}

@Composable
private fun StateView(state: SpeedTestState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E2E))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Per-phase progress: download bar + upload bar (each 0..1).
        // Combined "overall" progress weights download as 0..50% and upload as 50..100%.
        val (dlProgress, ulProgress) = when (state) {
            is SpeedTestState.Downloading -> state.progress to 0f
            is SpeedTestState.Uploading -> 1f to state.progress
            is SpeedTestState.Finished -> 1f to 1f
            else -> 0f to 0f
        }
        val overall = (dlProgress * 0.5f) + (ulProgress * 0.5f)

        val (phase, primary, sub) = when (state) {
            is SpeedTestState.Idle -> Triple("Idle", "—", "")
            is SpeedTestState.Connecting -> Triple("Connecting…", "—", "")
            is SpeedTestState.MeasuringPing -> Triple(
                "Measuring Ping",
                "${state.currentMedian} ms",
                "samples: ${state.samples.size}",
            )
            is SpeedTestState.Probing -> Triple("Probing…", "—", "")
            is SpeedTestState.Downloading -> Triple(
                "Downloading  ${(state.progress * 100).toInt()}%",
                "%.1f Mbps".format(state.currentMbps),
                "peak %.1f  •  conns %d  •  %.1fs".format(
                    state.peakMbps, state.connections, state.elapsedMs / 1000.0
                ),
            )
            is SpeedTestState.Uploading -> Triple(
                "Uploading  ${(state.progress * 100).toInt()}%",
                "%.1f Mbps".format(state.currentMbps),
                "peak %.1f  •  conns %d  •  %.1fs".format(
                    state.peakMbps, state.connections, state.elapsedMs / 1000.0
                ),
            )
            is SpeedTestState.Finished -> Triple(
                "Finished  100%",
                "%.1f Mbps".format(state.result.download.finalMbps),
                "DL final",
            )
            is SpeedTestState.Error -> Triple(
                "Error in ${state.phase}",
                "—",
                state.throwable.message ?: "Unknown error",
            )
        }

        Text(text = phase, color = Color(0xFF00BCD4), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = primary, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotEmpty()) {
            Text(text = sub, color = Color(0xFF9E9E9E), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(8.dp))

        // Overall progress (DL=first half, UL=second half)
        Text(
            text = "Overall  ${(overall * 100).toInt()}%",
            color = Color(0xFF9E9E9E),
            fontSize = 11.sp,
        )
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = overall,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF00BCD4),
            trackColor = Color(0xFF333344),
        )

        Spacer(Modifier.height(4.dp))

        // Per-phase: download
        Text(text = "Download  ${(dlProgress * 100).toInt()}%", color = Color(0xFF9E9E9E), fontSize = 11.sp)
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = dlProgress,
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Color(0xFF00BCD4),
            trackColor = Color(0xFF333344),
        )

        // Per-phase: upload
        Text(text = "Upload  ${(ulProgress * 100).toInt()}%", color = Color(0xFF9E9E9E), fontSize = 11.sp)
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = ulProgress,
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Color(0xFF9C27B0),
            trackColor = Color(0xFF333344),
        )

        // Final result block
        if (state is SpeedTestState.Finished) {
            Spacer(Modifier.height(8.dp))
            Divider(color = Color(0xFF333344))
            Spacer(Modifier.height(8.dp))
            val r = state.result
            Text(
                text = """
                    Download : %.2f Mbps  (peak %.2f)
                    Upload   : %.2f Mbps  (peak %.2f)
                    Ping     : %.1f ms  (jitter %.1f ms)
                    Min/Max  : %d / %d ms
                    DL time  : %d ms
                    UL time  : %d ms
                """.trimIndent().format(
                    r.download.finalMbps, r.download.peakMbps,
                    r.upload.finalMbps, r.upload.peakMbps,
                    r.ping.avg, r.ping.jitter,
                    r.ping.min, r.ping.max,
                    r.download.durationMs, r.upload.durationMs,
                ),
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
