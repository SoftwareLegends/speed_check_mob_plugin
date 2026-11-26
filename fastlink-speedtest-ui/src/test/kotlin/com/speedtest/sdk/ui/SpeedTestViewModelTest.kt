package com.speedtest.sdk.ui

import com.speedtest.sdk.SpeedTestConfig
import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.SpeedTestState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedTestViewModelTest {

    private class FakeRunner : SpeedTestRunner {
        override val state = MutableStateFlow<SpeedTestState>(SpeedTestState.Idle)
        var cancelCount = 0
        var testCancelled = false
        override suspend fun runFullTest(): SpeedTestResult = try {
            awaitCancellation()
        } catch (e: CancellationException) {
            testCancelled = true
            throw e
        }
        override fun cancel() { cancelCount++ }
    }

    private val created = mutableListOf<FakeRunner>()
    private fun newVm() = SpeedTestViewModel { created += FakeRunner(); created.last() }
    private val cfg = SpeedTestConfig(baseUrl = "https://example.test")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun forwardsStateOfCurrentClient() {
        val vm = newVm()
        vm.configure(cfg)
        created[0].state.value = SpeedTestState.Error("ping", RuntimeException("x"))
        assertTrue(vm.state.value is SpeedTestState.Error)
    }

    @Test fun reconfigureIgnoresOldClientAndCancelsIt() {
        val vm = newVm()
        vm.configure(cfg)
        vm.configure(cfg.copy(baseUrl = "https://other.test"))
        val (a, b) = created
        assertEquals(1, a.cancelCount)
        a.state.value = SpeedTestState.Error("old", RuntimeException("x"))
        assertEquals(SpeedTestState.Idle, vm.state.value)
        b.state.value = SpeedTestState.Error("new", RuntimeException("y"))
        assertEquals("new", (vm.state.value as SpeedTestState.Error).phase)
    }

    @Test fun sameConfigKeepsRunningTest() {
        val vm = newVm()
        vm.configure(cfg)
        vm.startTest()
        created[0].state.value = SpeedTestState.Error("mid", RuntimeException("x"))
        vm.configure(cfg)
        assertEquals(1, created.size)
        assertEquals(0, created[0].cancelCount)
        assertEquals(false, created[0].testCancelled)
        assertEquals("mid", (vm.state.value as SpeedTestState.Error).phase)
    }

    @Test fun onClearedCancelsRunningTest() {
        val vm = newVm()
        vm.configure(cfg)
        vm.startTest()
        val clearedMethod = SpeedTestViewModel::class.java.getDeclaredMethod("onCleared")
        clearedMethod.isAccessible = true
        clearedMethod.invoke(vm)
        assertEquals(1, created[0].cancelCount)
        assertTrue(created[0].testCancelled)
        created[0].state.value = SpeedTestState.Error("late", RuntimeException("z"))
        assertEquals(SpeedTestState.Idle, vm.state.value)
    }
}
