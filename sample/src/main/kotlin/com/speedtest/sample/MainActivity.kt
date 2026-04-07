package com.speedtest.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.ui.SpeedTestScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(),
            ) {
                SampleApp()
            }
        }
    }
}

@Composable
private fun SampleApp() {
    var serverUrl by remember { mutableStateOf("https://speed.sl8.org") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showTest by remember { mutableStateOf(false) }

    if (showTest) {
        SpeedTestScreen(
            config = SpeedTestConfig(
                baseUrl = serverUrl,
                username = username.ifBlank { null },
                password = password.ifBlank { null },
            ),
            onFinished = { result ->
                Log.d(
                    "SpeedTest",
                    "DL: %.1f Mbps, UL: %.1f Mbps, Ping: %.0f ms".format(
                        result.download.finalMbps,
                        result.upload.finalMbps,
                        result.ping.avg,
                    )
                )
            },
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121220))
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00BCD4),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00BCD4),
                unfocusedLabelColor = Color.Gray,
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (Basic Auth)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (Basic Auth)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { showTest = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00BCD4),
                ),
            ) {
                Text("Start Speed Test")
            }
        }
    }
}
