package com.speedtest.sdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedtest.sdk.SpeedTestClient
import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.SpeedTestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel wrapping [SpeedTestClient] for use in Compose screens.
 */
@HiltViewModel
class SpeedTestViewModel @Inject constructor() : ViewModel() {

    private val _config = MutableStateFlow<SpeedTestConfig?>(null)
    private var client: SpeedTestClient? = null

    private val _state = MutableStateFlow<SpeedTestState>(SpeedTestState.Idle)

    /** Current test state. Observe for UI updates. */
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    /**
     * Configure the SDK with a server URL and optional credentials.
     */
    fun configure(config: SpeedTestConfig) {
        _config.value = config
        client = SpeedTestClient(config)

        // Forward client state to our state
        viewModelScope.launch {
            client?.state?.collect { _state.value = it }
        }
    }

    /**
     * Start a full speed test.
     */
    fun startTest() {
        val currentClient = client ?: return
        viewModelScope.launch {
            try {
                currentClient.runFullTest()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Error already emitted via state
            }
        }
    }

    /**
     * Cancel a running test.
     */
    fun cancel() {
        client?.cancel()
    }
}
