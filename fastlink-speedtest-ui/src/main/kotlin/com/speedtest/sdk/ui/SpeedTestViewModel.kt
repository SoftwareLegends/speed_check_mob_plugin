package com.speedtest.sdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedtest.sdk.SpeedTestClient
import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.SpeedTestState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Minimal engine surface the ViewModel needs. Intended for tests and custom engines. */
interface SpeedTestRunner {
    val state: StateFlow<SpeedTestState>
    suspend fun runFullTest(): SpeedTestResult
    fun cancel()
}

internal class ClientRunner(private val client: SpeedTestClient) : SpeedTestRunner {
    override val state: StateFlow<SpeedTestState> get() = client.state
    override suspend fun runFullTest(): SpeedTestResult = client.runFullTest()
    override fun cancel() = client.cancel()
}

/**
 * ViewModel wrapping [SpeedTestClient] for use in Compose screens.
 *
 * Plain AndroidX ViewModel: no DI framework required. Obtain it with
 * `viewModel()` (default AndroidX factory, no-arg constructor) in any app.
 */
class SpeedTestViewModel(
    private val runnerFactory: (SpeedTestConfig) -> SpeedTestRunner = { ClientRunner(SpeedTestClient(it)) },
) : ViewModel() {

    private var runner: SpeedTestRunner? = null
    private var currentConfig: SpeedTestConfig? = null
    private var collectorJob: Job? = null
    private var testJob: Job? = null

    private val _state = MutableStateFlow<SpeedTestState>(SpeedTestState.Idle)

    /** Current test state. Observe for UI updates. */
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    /**
     * Configure the SDK with a server URL and optional credentials.
     * Calling it again with an equal config is a no-op (e.g. after rotation, so a
     * running test survives); a different config cancels the previous client and
     * its state collector.
     */
    fun configure(config: SpeedTestConfig) {
        if (config == currentConfig && runner != null) return
        currentConfig = config
        collectorJob?.cancel()
        testJob?.cancel()
        runner?.cancel()

        val newRunner = runnerFactory(config)
        runner = newRunner
        _state.value = SpeedTestState.Idle
        collectorJob = viewModelScope.launch {
            newRunner.state.collect { _state.value = it }
        }
    }

    /**
     * Start a full speed test.
     */
    fun startTest() {
        val current = runner ?: return
        testJob?.cancel()
        testJob = viewModelScope.launch {
            try {
                current.runFullTest()
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
        runner?.cancel()
    }

    override fun onCleared() {
        collectorJob?.cancel()
        testJob?.cancel()
        runner?.cancel()
        runner = null
        currentConfig = null
        super.onCleared()
    }
}
