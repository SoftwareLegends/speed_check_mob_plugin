# How the SDK calculates the final Download / Upload value

> "After the 30 seconds, what number do we actually show — average, peak, last sample…?"

**Short answer:** neither average nor peak. We show the **highest *sustained* speed** the
connection held for at least **3 seconds**, clamped by the observed peak. If no bucket
qualifies, we fall back to peak, then to a 10%-trimmed mean. The implementation lives in
[`SustainedScorer.score`](src/main/kotlin/com/speedtest/sdk/math/SustainedScorer.kt).

This document walks through every step from the raw bytes to the final number so the
team can reason about edge cases, debug user reports, and tweak constants safely.

---

## 1. The big picture

```
 ┌──────────────┐   bytes      ┌─────────────┐  raw Mbps   ┌────────────┐  smoothed   ┌──────────────────┐
 │ N workers    │ ───────────▶ │ SpeedSampler│ ──────────▶ │ EWMA filter│ ──────────▶ │ List<SpeedSample>│
 │ (downloading │              │  every 200ms│             │  α = 0.30  │             │  (timestamp+Mbps)│
 │  in parallel)│              └─────────────┘             └────────────┘             └──────────────────┘
 └──────────────┘                                                                              │
                                                                                               ▼
                                                              ┌─────────────────────────────────────────────┐
                                                              │ drop warm-up samples → keep "scoring" window │
                                                              └─────────────────────────────────────────────┘
                                                                                               │
                                                                                               ▼
                                                              ┌─────────────────────────────────────────────┐
                                                              │  SustainedScorer: 0.5 Mbps buckets,         │
                                                              │  pick the highest bucket with ≥ 3 s of time │
                                                              └─────────────────────────────────────────────┘
                                                                                               │
                                                                                               ▼
                                                                                          finalMbps
```

The same pipeline is used for both `DownloadEngine` and `UploadEngine`. Only the data
source differs (HTTP GET vs. multipart POST).

---

## 2. Step 1 — Raw bytes from many parallel connections

Both engines launch **N parallel workers** based on the connection `Tier`
(`tier.conn` initial workers, ramped up to `tier.maxConn` over time —
see `DownloadEngine.kt:36-42` and the ramping block at `:65-73`).

Every worker shares **one atomic counter**:

```kotlin
class SpeedSampler {
    val totalBytes = AtomicLong(0L)   // incremented by all workers
    ...
}
```

So at any instant, `totalBytes` represents the **total bytes transferred across all
connections combined**. We never look at any single worker on its own — the throughput we
care about is the *aggregate* throughput of the connection.

---

## 3. Step 2 — Convert bytes to Mbps every 200 ms

The engine has a sampling loop that runs every 200 ms while the test is alive
(`DownloadEngine.kt:46-94`). On each tick it calls `sampler.sample()`:

```kotlin
fun sample(): Double {
    val now = System.currentTimeMillis()
    val deltaBytes = totalBytes.get() - lastSampleBytes
    val deltaTime  = now - lastSampleTime

    val rawSpeedMbps = (deltaBytes * 8.0) / (deltaTime * 1000.0)
    val smoothedSpeed = ewmaFilter.update(rawSpeedMbps)
    samples.add(SpeedSample(smoothedSpeed, now))
    ...
}
```

Two things to notice:

| Step              | What it does                                                                  | Why                                                                       |
| ----------------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| **Raw Mbps**      | `(bytes · 8) / (Δt_ms · 1000)` → megabits per second over the 200 ms slice   | Mbps = megabits / second; bytes·8 → bits, ÷1000 → ms-to-s + bytes-to-kilo |
| **EWMA smoothing**| `value = 0.3·new + 0.7·old` (see `EwmaFilter.kt`)                             | Cancels out the jitter from TCP slow-start, packet bursts, OS scheduling  |

After this step the sampler holds a list of `SpeedSample(smoothedMbps, timestamp)`,
roughly **5 samples per second**, for the entire test duration.

The number you see *live* in the UI (`SpeedTestState.Downloading.currentMbps`) is exactly
this smoothed value — that is why the gauge moves smoothly instead of jumping around.

---

## 4. Step 3 — Drop the warm-up window

The first second or two of any speed test is dominated by:

- TCP slow-start
- TLS handshake completion on later workers
- Connection ramping (we open more sockets in stages)

Including those samples would *underestimate* the true link speed. So before scoring we
throw them away:

```kotlin
val scoringSamples = sampler.getScoringsamples(startTime, tier.warmupMs)
```

`tier.warmupMs` lives in `model/Tier.kt`. Only samples whose timestamp is **after**
`startTime + warmupMs` are passed to the scorer. Everything earlier is ignored for the
final number (but the UI still saw it during the live phase).

---

## 5. Step 4 — Bucket scoring (the actual answer to the question)

This is the part the team needs to understand. It's done by
`SustainedScorer.score(samples, peakMbps)` and is **not** a mean, not a max, not a last-N
average. It's a "highest speed the link could hold long enough to count" estimator.

### 5.1 Bucket the samples

We chop the speed axis into **0.5 Mbps wide bins**:

```
bucket index = floor(speedMbps / 0.5)
```

So a sample of 87.3 Mbps falls in bucket #174 (which represents the 87.0–87.5 Mbps range).

For each consecutive pair of samples we add the **time delta between them** to the
bucket the *first* sample lives in:

```kotlin
for (i in 0 until samples.size - 1) {
    val bucketIndex = floor(samples[i].speedMbps / 0.5).toInt()
    val dt = samples[i + 1].timestampMs - samples[i].timestampMs
    bucketTime[bucketIndex] += dt
}
```

After the loop, `bucketTime` is a histogram: *"the connection spent X milliseconds in
the 87–87.5 Mbps band, Y milliseconds in the 86.5–87 Mbps band, …"*

### 5.2 Pick the highest bucket that lasted ≥ 3 seconds

```kotlin
val sustained = bucketTime.entries
    .filter { it.value >= 3_000 }      // MIN_SUSTAINED_MS
    .maxByOrNull { it.key }            // highest bucket index that survives the filter
```

That single line is the heart of the algorithm:

> *Find the highest 0.5 Mbps band where the connection held that speed for at least
> 3 seconds of cumulative time.*

A 3-second floor means transient spikes (a single 200 ms TCP burst that pushes us into
a high band for one sample) **cannot** influence the result. The link has to genuinely
sit there.

### 5.3 Convert bucket → Mbps and clamp

```kotlin
val sustainedMbps = sustained.key * 0.5         // 0.5 Mbps × bucket index
return min(sustainedMbps, peakMbps)             // never report higher than the observed peak
```

The clamp by `peakMbps` is a safety net — the bucket is the *lower edge* of the
0.5 Mbps band so the value can never exceed reality.

### 5.4 Fallback chain (graceful degradation)

If for some reason no bucket survived the 3-second filter (very short test, very
unstable link), we degrade gracefully:

```kotlin
return when {
    sustainedMbps != null -> min(sustainedMbps, peakMbps)
    peakMbps > 0.0        -> peakMbps                       // fallback #1
    else                  -> trimmedMean(samples...)        // fallback #2 (10% trimmed mean)
}
```

So the priority is:

1. **Sustained 0.5 Mbps bucket** (the normal happy path for any healthy link)
2. **Observed peak** (only if the test was so chaotic that no bucket reached 3 s)
3. **10%-trimmed mean** of all speeds (only if there was no peak either — basically a
   pathological case)

---

## 6. Why this method, and not "just the average"?

| Approach                           | Problem                                                                                                                              |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| **Plain average over 30 s**        | Drags the result down because of the slow-start / ramp-up samples and any temporary congestion. Underreports a fast link.            |
| **Peak / max sample**              | Overreports because a single 200 ms burst (TCP buffering on the OS side) can spike the value far above what the link actually holds. |
| **Last-N seconds average**         | Sensitive to *when* you stop. If congestion happens to land in the last 5 seconds, the number tanks even though the link is healthy. |
| **Median**                         | Robust but doesn't reward a connection that *climbs* into a higher tier — it just reports the middle of the distribution.            |
| **Sustained 0.5 Mbps bucket (us)** | Reports the *highest* speed the link genuinely held. Ignores both slow-start dips and one-shot spikes. Stable to early-stop.         |

It's the same idea Ookla and Cloudflare's clients use: estimate the link's **steady-state
capacity**, not its statistical average over a noisy interval.

---

## 7. Worked example

Imagine a 30-second download on a ~95 Mbps line. A simplified sample stream:

| Time (s) | Smoothed Mbps | Bucket idx (×0.5) | Notes                |
| -------: | ------------: | ----------------: | -------------------- |
|      0.2 |           4.1 |                 8 | TCP slow-start       |
|      0.4 |          18.6 |                37 | ramp                 |
|      0.6 |          51.2 |               102 | second worker joined |
|      0.8 |          78.9 |               157 |                      |
|      1.0 |          90.4 |               180 |                      |
|      1.2 |          93.1 |               186 |                      |
|      1.4 |          94.5 |               189 |                      |
|        … |             … |                 … |                      |
|      8.0 |          94.7 |               189 | flat                 |
|      8.2 |          83.2 |               166 | brief congestion     |
|      8.4 |          94.6 |               189 | recovered            |
|        … |             … |                 … |                      |
|     30.0 |          94.4 |               188 |                      |

After warm-up trimming (say `warmupMs = 2000`), the samples from 2 s onwards go into the
scorer.

Bucket histogram (only top buckets shown):

| Bucket idx | Mbps band     | Total time |
| ---------: | ------------- | ---------: |
|        189 | 94.5 – 95.0   |    24.6 s  |
|        188 | 94.0 – 94.5   |     2.8 s  |
|        187 | 93.5 – 94.0   |     0.4 s  |
|        186 | 93.0 – 93.5   |     0.2 s  |
|        166 | 83.0 – 83.5   |     0.2 s  |

- Bucket 189 has 24.6 s ≥ 3 s ✅ — qualifies
- Bucket 188 has  2.8 s  < 3 s ❌
- Highest qualifying bucket → 189 → `189 * 0.5 = 94.5 Mbps`
- `min(94.5, peakMbps=94.7)` → **`finalMbps = 94.5 Mbps`**

That is the value the SDK returns in `SpeedTestResult.download.finalMbps` and that the UI
shows on the result screen.

---

## 8. Other fields in `PhaseResult`

For debugging / analytics we also expose:

| Field               | Meaning                                                              | When to use it                                  |
| ------------------- | -------------------------------------------------------------------- | ----------------------------------------------- |
| `finalMbps`         | The sustained-bucket score described above                           | **The number to show users.**                   |
| `peakMbps`          | Highest single smoothed sample observed during the run               | Burst capability / debugging                    |
| `trimmedMeanMbps`   | 10% trimmed mean of the scoring window                               | Sanity check; close to `finalMbps` if link flat |
| `durationMs`        | Wall-clock duration of the phase                                     | Useful with early-stop                          |

The trimmed mean is computed inline in both `DownloadEngine.kt:106-111` and
`UploadEngine.kt:101-106`. It is **not** the user-facing number — it's just an extra
diagnostic field on `PhaseResult`.

---

## 9. Why a phase can finish before 30 s — early-stop

The test does **not** always run a full 30 seconds. Both engines look at the
`SpeedSampler.isStable(holdMs = 3000)` flag and break out early if:

- We've already run at least `tier.minRunMs`, **and**
- The smoothed speed has stayed within ±3% of the peak for ≥ 3 seconds.

That's defined in `SpeedSampler.kt:62-69`:

```kotlin
val currentlyStable = smoothedSpeed >= peakMbps * 0.95 &&
                      smoothedSpeed <= peakMbps * 1.003
```

So a fast, well-behaved link finishes in ~10–15 seconds; a noisy or congested one runs
the full 30 s. Either way the same scoring rules apply — the difference is just *how
many* samples land in the histogram.

---

## 10. Tunable constants — quick reference

| Constant                     | Where                              | Default                  | What it affects                              |
| ---------------------------- | ---------------------------------- | ------------------------ | -------------------------------------------- |
| `BUCKET_WIDTH`               | `SustainedScorer.kt`               | `0.5` Mbps               | Granularity of the histogram                 |
| `MIN_SUSTAINED_MS`           | `SustainedScorer.kt`               | `3_000` ms               | "Sustained" definition (≥ 3 s required)     |
| EWMA `alpha`                 | `SpeedSampler.kt` → `EwmaFilter`   | `0.3`                    | Live-Mbps smoothing aggressiveness          |
| Sample interval              | `DownloadEngine.kt` / `UploadEngine.kt` | `200` ms (delay loop) | How often we add a histogram point          |
| `warmupMs`                   | `Tier`                             | tier-dependent           | How much of the start we throw away         |
| `maxMs`                      | `DownloadEngine` / `UploadEngine`  | `30_000` ms              | Hard cap on the phase duration              |
| `minRunMs`                   | `Tier`                             | tier-dependent           | Earliest time we are allowed to early-stop  |
| Stability ±band              | `SpeedSampler.kt`                  | `[0.95, 1.003] · peak`   | What counts as "stable" for early-stop      |
| Stability hold               | `DownloadEngine.kt` / `UploadEngine.kt` | `3_000` ms          | How long stability must hold before stopping|

---

## TL;DR for the teammate who asked

> *"Did we average it, or take the highest?"*

Neither. Every 200 ms we measure the aggregate throughput across all parallel
connections, smooth it with an EWMA filter, and bucket it into 0.5 Mbps bins. After
discarding the warm-up samples, we pick **the highest 0.5 Mbps bin where the connection
spent at least 3 seconds total**, and report that as `finalMbps`. It's the highest speed
the link could *sustain*, not the highest speed it could *touch* and not the average.

If you want to follow the code: start at
[`DownloadEngine.run()`](src/main/kotlin/com/speedtest/sdk/engine/DownloadEngine.kt) →
[`SpeedSampler.sample()`](src/main/kotlin/com/speedtest/sdk/math/SpeedSampler.kt) →
[`SustainedScorer.score()`](src/main/kotlin/com/speedtest/sdk/math/SustainedScorer.kt).
