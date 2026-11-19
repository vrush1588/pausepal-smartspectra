package com.app.pausapal.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * Where the newest sample of a breathing trace sits between the recent min and max, as 0..1
 * (0 = most "out", 1 = most "in"; the traces go up on inhale). Null if there is not enough data
 * or the trace is flat.
 */
fun breathPhase(trace: List<Float>, minSamples: Int = 20): Float? {
    if (trace.size < minSamples) return null
    var lo = Float.POSITIVE_INFINITY
    var hi = Float.NEGATIVE_INFINITY
    for (v in trace) {
        if (v < lo) lo = v
        if (v > hi) hi = v
    }
    val range = hi - lo
    if (!range.isFinite() || range < 1e-6f) return null
    return ((trace.last() - lo) / range).coerceIn(0f, 1f)
}

/**
 * Smoothed live breathing phase (0..1) from the belly wave, falling back to the chest wave.
 * Null when there is no live signal, so callers can fall back to a fixed animation.
 * The spring acts as a low-pass filter so the motion does not jitter.
 */
@Composable
fun rememberBreathPhase(chest: List<Float>, belly: List<Float>, live: Boolean): Float? {
    val raw = if (live) breathPhase(belly) ?: breathPhase(chest) else null
    val smooth by animateFloatAsState(
        targetValue = raw ?: 0.5f,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow),
        label = "breathPhase",
    )
    return if (raw == null) null else smooth
}
