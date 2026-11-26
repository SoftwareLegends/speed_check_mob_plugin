package com.speedtest.sdk.bridge

import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.SpeedTestState
import com.speedtest.sdk.model.PhaseResult
import com.speedtest.sdk.model.PingStats
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedTestSessionTest {
    private val result = SpeedTestResult(
        ping = PingStats(12.5, 12.0, 20.0, 1.5, 10, 30, listOf(10, 12, 30)),
        download = PhaseResult(90.0, 100.0, 85.0, 10_000),
        upload = PhaseResult(40.0, 45.0, 38.0, 10_000),
        serverUrl = "https://speed.example.com",
        timestamp = 1L,
    )

    private class Fake(val body: suspend Fake.() -> SpeedTestResult) : SpeedTestSession.Runner {
        val flow = MutableStateFlow<SpeedTestState>(SpeedTestState.Idle)
        override val state: StateFlow<SpeedTestState> = flow
        var cancelCalls = 0
        override suspend fun run() = body()
        override fun cancel() { cancelCalls++ }
    }

    private class Harness(scope: TestScope) {
        val events = mutableListOf<Map<String, Any?>>()
        var ok: Map<String, Any?>? = null
        var failure: Pair<String, String>? = null
        var outcomes = 0
        val session = SpeedTestSession(scope, { scope.testScheduler.currentTime }) { events += it }
        fun start(r: SpeedTestSession.Runner, overall: Long = 10_000, interval: Long = 0) =
            session.start(r, overall, interval, { ok = it; outcomes++ }, { c, m -> failure = c to m; outcomes++ })
        val phases get() = events.map { it["phase"] }
    }

    @Test fun phasesInOrderThenFinished() = runTest {
        val h = Harness(this)
        val f = Fake {
            flow.value = SpeedTestState.Connecting; yield()
            flow.value = SpeedTestState.MeasuringPing(listOf(10), 10); yield()
            flow.value = SpeedTestState.Probing; yield()
            flow.value = SpeedTestState.Downloading(50.0, 60.0, 1, 4, 0.5f); yield()
            flow.value = SpeedTestState.Uploading(20.0, 25.0, 1, 2, 0.5f); yield()
            result
        }
        assertTrue(h.start(f)); advanceUntilIdle()
        assertEquals(listOf("connecting", "ping", "probe", "download", "upload", "finished"), h.phases.filter { it != "idle" })
        assertEquals(90.0, h.ok!!["download"]); assertEquals(1, h.outcomes)
    }

    @Test fun stopCancelsClientAndRejectsCancelled() = runTest {
        val h = Harness(this)
        val f = Fake { flow.value = SpeedTestState.Connecting; awaitCancellation() }
        h.start(f); runCurrent()
        h.session.stop(); advanceUntilIdle()
        assertEquals(1, f.cancelCalls)
        assertEquals("CANCELLED", h.failure!!.first)
        assertEquals(1, h.outcomes)
        assertEquals("cancelled", h.phases.last())
        assertEquals(1, h.phases.count { it == "cancelled" || it == "error" })
    }

    @Test fun overallTimeoutRejectsTimeoutWithOneErrorEvent() = runTest {
        val h = Harness(this)
        h.start(Fake { delay(60_000); result }, overall = 1_000); advanceUntilIdle()
        assertEquals("TIMEOUT", h.failure!!.first)
        assertEquals(1, h.phases.count { it == "error" })
        @Suppress("UNCHECKED_CAST") val err = h.events.last()["error"] as Map<String, Any?>
        assertEquals("overall", err["phase"]); assertEquals("TIMEOUT", err["code"])
    }

    @Test fun phaseErrorProducesSingleErrorEventWithRealPhase() = runTest {
        val h = Harness(this)
        val f = Fake {
            val e = IOException("boom https://u:pw@h/x")
            flow.value = SpeedTestState.Error("download", e)
            throw e
        }
        h.start(f); advanceUntilIdle()
        assertEquals(1, h.phases.count { it == "error" })
        @Suppress("UNCHECKED_CAST") val err = h.events.last()["error"] as Map<String, Any?>
        assertEquals("download", err["phase"]); assertEquals("NETWORK", err["code"])
        assertEquals("NETWORK", h.failure!!.first)
        assertFalse(h.failure!!.second.contains("pw"))
    }

    @Test fun throttleKeepsPhaseChangesAndFinalProgress() = runTest {
        val h = Harness(this)
        val f = Fake {
            flow.value = SpeedTestState.Connecting; yield()
            flow.value = SpeedTestState.MeasuringPing(listOf(10), 10); yield()
            flow.value = SpeedTestState.Probing; yield()
            flow.value = SpeedTestState.Downloading(1.0, 1.0, 1, 1, 0.2f); yield()
            flow.value = SpeedTestState.Downloading(2.0, 2.0, 1, 1, 0.4f); yield()
            flow.value = SpeedTestState.Downloading(3.0, 3.0, 1, 1, 1.0f); yield()
            result
        }
        h.start(f, interval = 500); advanceUntilIdle()
        val p = h.phases.filter { it != "idle" }
        assertEquals(listOf("connecting", "ping", "probe", "download", "download", "finished"), p)
        assertEquals(1.0, h.events.filter { it["phase"] == "download" }.last()["progress"])
    }

    @Test fun secondStartWhileRunningIsRejected() = runTest {
        val h = Harness(this)
        h.start(Fake { awaitCancellation() }); runCurrent()
        assertFalse(h.start(Fake { result }))
        h.session.stop(); advanceUntilIdle()
        assertTrue(h.start(Fake { result })); advanceUntilIdle()
        assertEquals(2, h.outcomes)
    }
}
