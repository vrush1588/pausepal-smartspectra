package com.app.pausapal.session

/**
 * Personal-baseline break suggestion logic. Pure Kotlin, no Android, memory only.
 *
 * - Baseline = average of breathing readings from 30 s to [baselineEndMs] into the session
 *   (60 s for 3 min sessions, 90 s for 5 and 10 min sessions). If fewer than
 *   [MIN_BASELINE_SAMPLES] readings arrived in that window, the window keeps extending until
 *   enough readings arrive.
 * - [resetBaseline] discards the baseline and restarts the window from "now" (used when the
 *   camera was paused for more than 60 s).
 * - After the baseline is set: a reading is "above baseline" when
 *   rate > baseline * 1.25 AND rate >= baseline + 3 /min.
 * - Two consecutive new above-baseline readings, outside the snooze/cooldown, => suggest a break.
 */
class BreakCoach(
    val baselineEndMs: Long,
    val snoozeMs: Long,
) {
    constructor(length: SessionLength) : this(length.baselineEndMs, length.snoozeMs)

    val baselineStartMs: Long = 30_000

    /** Session time the baseline window is measured from (0, or the time of the last reset). */
    private var windowOriginMs = 0L
    private var sum = 0.0
    private var count = 0
    private var consecutiveHigh = 0
    private var quietUntilMs = 0L

    var baseline: Double? = null
        private set

    /** True while still before the start of the (current) baseline window. */
    fun warmingUp(elapsedMs: Long): Boolean = elapsedMs - windowOriginMs < baselineStartMs

    /**
     * Feed a NEW breathing reading. Returns true if the break sheet should be shown now.
     * @param elapsedMs time since the session started
     */
    fun onBreathing(rate: Double, elapsedMs: Long): Boolean {
        val base = baseline
        if (base == null) {
            val t = elapsedMs - windowOriginMs
            if (t >= baselineStartMs) {
                sum += rate; count++
                if (t >= baselineEndMs && count >= MIN_BASELINE_SAMPLES) baseline = sum / count
            }
            return false
        }
        val high = rate > base * 1.25 && rate >= base + 3.0
        consecutiveHigh = if (high) consecutiveHigh + 1 else 0
        if (consecutiveHigh >= 2 && elapsedMs >= quietUntilMs) {
            consecutiveHigh = 0
            return true
        }
        return false
    }

    /** "Not now" or "Take a break": no new suggestion for [snoozeMs]. */
    fun quietFor(elapsedMs: Long) {
        quietUntilMs = elapsedMs + snoozeMs
        consecutiveHigh = 0
    }

    /** Camera paused: readings either side of the gap must not count as "consecutive". */
    fun onCameraGap() {
        consecutiveHigh = 0
    }

    /** Throw away the baseline (and any partial window) and learn a new one starting at [elapsedMs]. */
    fun resetBaseline(elapsedMs: Long) {
        windowOriginMs = elapsedMs
        sum = 0.0; count = 0
        consecutiveHigh = 0
        baseline = null
    }

    companion object {
        const val MIN_BASELINE_SAMPLES = 3
    }
}
