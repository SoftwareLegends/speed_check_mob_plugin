package com.speedtest.sdk.bridge

import com.speedtest.sdk.SpeedTestResult
import com.speedtest.sdk.SpeedTestState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Runs one speed test at a time and turns it into bridge events + a single outcome.
 * Plain Kotlin (no Expo / React Native types) so it can be unit tested with a fake [Runner].
 *
 * Guarantees: phase changes are never throttled away; exactly one terminal event
 * (`finished` / `error` / `cancelled`) per test; the outcome callback is called exactly once.
 */
class SpeedTestSession(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val emit: (Map<String, Any?>) -> Unit,
) {
    /** What the session needs from a speed test client. */
    interface Runner {
        val state: StateFlow<SpeedTestState>
        suspend fun run(): SpeedTestResult
        fun cancel()
    }

    private val running = AtomicBoolean(false)
    @Volatile private var job: Job? = null
    @Volatile private var runner: Runner? = null

    /**
     * Starts a test. Returns false (and calls nothing) if one is already running; the caller
     * should reject with [SpeedTestEventMapper.CODE_BUSY].
     */
    fun start(
        runner: Runner,
        overallTimeoutMs: Long,
        reportIntervalMs: Long,
        onResult: (Map<String, Any?>) -> Unit,
        onError: (code: String, message: String) -> Unit,
    ): Boolean {
        if (!running.compareAndSet(false, true)) return false
        this.runner = runner
        // ATOMIC: the body always runs, so the outcome callback fires even if stop() comes first.
        job = scope.launch(start = CoroutineStart.ATOMIC) {
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                var lastPhase: Any? = null
                var lastAt = 0L
                runner.state.collect { s ->
                    if (s is SpeedTestState.Finished || s is SpeedTestState.Error) return@collect
                    val event = SpeedTestEventMapper.toEvent(s)
                    val phase = event["phase"]
                    val done = (event["progress"] as? Double ?: 0.0) >= 1.0
                    val now = clock()
                    if (phase != lastPhase || done || reportIntervalMs <= 0 || now - lastAt >= reportIntervalMs) {
                        lastPhase = phase
                        lastAt = now
                        emit(event)
                    }
                }
            }
            try {
                val r = withTimeout(overallTimeoutMs) { runner.run() }
                stopCollector(collector)
                emit(SpeedTestEventMapper.toEvent(SpeedTestState.Finished(r)))
                onResult(SpeedTestEventMapper.toResult(r))
            } catch (e: TimeoutCancellationException) {
                stopCollector(collector)
                fail(runner, "overall", e, "Speed test timed out", onError)
            } catch (e: CancellationException) {
                stopCollector(collector)
                // stopTest() or module destroyed: always settle the promise.
                emit(SpeedTestEventMapper.cancelledEvent())
                onError(SpeedTestEventMapper.CODE_CANCELLED, "Speed test cancelled")
            } catch (e: Throwable) {
                stopCollector(collector)
                fail(runner, "test", e, SpeedTestEventMapper.sanitize(e.message ?: e.javaClass.simpleName), onError)
            } finally {
                this@SpeedTestSession.runner = null
                running.set(false)
            }
        }
        return true
    }

    // Join so no progress event can arrive after the terminal event.
    private suspend fun stopCollector(c: Job) = withContext(NonCancellable) { c.cancelAndJoin() }

    private fun fail(
        runner: Runner,
        fallbackPhase: String,
        e: Throwable,
        message: String,
        onError: (String, String) -> Unit,
    ) {
        val phase = (runner.state.value as? SpeedTestState.Error)?.phase ?: fallbackPhase
        emit(SpeedTestEventMapper.errorEvent(phase, e))
        onError(SpeedTestEventMapper.codeFor(e), message)
    }

    /** Cancels the running test; its outcome is `CANCELLED`. No-op when idle. */
    fun stop() {
        val r = runner ?: return
        r.cancel()
        job?.cancel()
    }
}
