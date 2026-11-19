package com.app.pausapal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.app.pausapal.session.SessionSummary
import com.app.pausapal.ui.components.Glyph
import com.app.pausapal.ui.components.GlyphBadge
import com.app.pausapal.ui.components.Mascot
import com.app.pausapal.ui.components.MascotMood
import com.app.pausapal.ui.components.PpCardBox
import com.app.pausapal.ui.components.PrimaryButton
import com.app.pausapal.ui.theme.MetricStyle
import com.app.pausapal.ui.theme.PpBorder
import com.app.pausapal.ui.theme.PpCoral
import com.app.pausapal.ui.theme.PpHigh
import com.app.pausapal.ui.theme.PpMint
import com.app.pausapal.ui.theme.PpTeal
import com.app.pausapal.ui.theme.PpText
import com.app.pausapal.ui.theme.PpText2
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Sessions shorter than this get the "Check-in complete" title and the "too short" line. */
private const val FULL_SESSION_MS = 60_000L

/** One reading shown in the "Your readings" card. */
private data class Reading(
    val label: String,
    val unit: String,
    /** "avg" or "last": what the number is. */
    val tag: String,
    val value: Double?,
    val decimals: Int,
    /** Shown instead of a number when there is no value. */
    val needs: String,
    /** One neutral, plain sentence. No good/bad framing. */
    val explain: String,
    val samples: List<Float>,
    val color: Color,
)

@Composable
fun SummaryScreen(summary: SessionSummary, onStartNew: () -> Unit) {
    val minutes = (summary.elapsedMs / 60_000.0).roundToInt()
    val totalSec = summary.elapsedMs / 1000
    val fullSession = summary.elapsedMs >= FULL_SESSION_MS

    val readings = listOf(
        Reading(
            "Pulse", "bpm", "avg", summary.avgPulse, 0, "Needs ~12 s",
            "Heartbeats per minute, estimated by the camera from tiny colour changes in your face.",
            summary.pulseSamples, PpCoral,
        ),
        Reading(
            "Breathing", "/min", "avg", summary.avgBreathing, 1, "Needs ~30 s",
            "Breaths per minute, estimated from the gentle rise and fall of your chest and belly.",
            summary.breathingSamples, PpTeal,
        ),
        Reading(
            "HRV", "ms", "last", summary.lastHrv, 0, "Needs ~60 s",
            "How much the time between heartbeats varies (RMSSD); it shifts naturally from moment to moment.",
            summary.hrvSamples, PpMint,
        ),
    )

    // Count-up: one shared 0 -> 1 progress, played once when the screen opens.
    val countUp = remember { Animatable(0f) }
    LaunchedEffect(Unit) { countUp.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TappableMascot(greetOnOpen = fullSession)
        Text(
            if (fullSession) "Nice session!" else "Check-in complete",
            style = MaterialTheme.typography.headlineLarge, color = PpText,
        )
        Text(
            buildAnnotatedString {
                append("You spent ")
                withStyle(SpanStyle(color = PpTeal)) { append(if (minutes < 1) "under 1 min" else "$minutes min") }
                append(" checking in")
            },
            style = MaterialTheme.typography.bodyLarge, color = PpText2, textAlign = TextAlign.Center,
        )

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Session length", "${totalSec / 60}m ${totalSec % 60}s", Modifier.weight(1f).fillMaxHeight())
            Stat("Breaks taken", "${summary.breaksTaken}", Modifier.weight(1f).fillMaxHeight())
        }

        ReadingsCard(readings, fullSession, countUp.value)

        PpCardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                GlyphBadge(Glyph.Leaf, PpTeal, 40.dp)
                Spacer(Modifier.padding(start = 12.dp))
                Column {
                    if (summary.baseline != null) {
                        Text(
                            buildAnnotatedString {
                                append("Your baseline breathing: ")
                                withStyle(SpanStyle(color = PpTeal)) { append("~${summary.baseline.roundToInt()} /min") }
                            },
                            style = MaterialTheme.typography.titleMedium, color = PpText,
                        )
                        Text("(yours, not a medical norm)", style = MaterialTheme.typography.bodyMedium, color = PpText2)
                    } else {
                        Text("Your baseline breathing: not set", style = MaterialTheme.typography.titleMedium, color = PpText)
                        Text(
                            "Not enough breathing readings this session to learn your baseline.",
                            style = MaterialTheme.typography.bodyMedium, color = PpText2,
                        )
                    }
                }
            }
        }

        PpCardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                GlyphBadge(Glyph.Shield, PpCoral, 40.dp)
                Spacer(Modifier.padding(start = 12.dp))
                Text(
                    "Wellness information only. Not medical advice. Nothing is saved: these numbers " +
                        "are erased as soon as you leave this screen.",
                    style = MaterialTheme.typography.bodyMedium, color = PpText2,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton("Start new session", onClick = onStartNew)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------------------------
//  "Your readings": three equal-height tiles + one expandable explanation panel.
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReadingsCard(readings: List<Reading>, fullSession: Boolean, progress: Float) {
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    val anyValue = readings.any { it.value != null }

    PpCardBox(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text("Your readings", style = MaterialTheme.typography.titleLarge, color = PpText)
        Spacer(Modifier.height(12.dp))

        if (!fullSession && !anyValue) {
            // Too short for any reading: one line instead of three empty tiles.
            Text(TOO_SHORT, style = MaterialTheme.typography.bodyMedium, color = PpText2)
            return@PpCardBox
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            readings.forEachIndexed { i, r ->
                ReadingTile(
                    r, progress, selected = expanded == i,
                    onClick = { expanded = if (expanded == i) null else i },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        // Keep the last panel's content while it collapses.
        var lastShown by remember { mutableStateOf<Int?>(null) }
        expanded?.let { lastShown = it }
        AnimatedVisibility(
            visible = expanded != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            lastShown?.let { DetailPanel(readings[it]) }
        }

        if (!fullSession) {
            Spacer(Modifier.height(12.dp))
            Text(TOO_SHORT, style = MaterialTheme.typography.bodyMedium, color = PpText2)
        }
    }
}

private const val TOO_SHORT = "This check-in was too short for full readings. Try 3 minutes."

private val TileShape = RoundedCornerShape(16.dp)

@Composable
private fun ReadingTile(r: Reading, progress: Float, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val valueText = r.value?.let { "%.${r.decimals}f".format(Locale.US, it * progress) }
    val a11y = if (r.value != null) {
        "${r.label}, ${"%.${r.decimals}f".format(Locale.US, r.value)} ${r.unit}, ${r.tag}"
    } else {
        "${r.label}, ${r.needs}"
    }
    Column(
        modifier
            .clip(TileShape)
            .background(PpHigh)
            .border(1.dp, if (selected) r.color.copy(alpha = 0.6f) else PpBorder, TileShape)
            .clickable(role = Role.Button, onClickLabel = if (selected) "Hide details" else "Show details", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = a11y
                stateDescription = if (selected) "Expanded" else "Collapsed"
            }
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        OneLine(r.label, MaterialTheme.typography.labelMedium.copy(color = PpText2))
        Spacer(Modifier.height(6.dp))
        if (valueText != null) {
            OneLine(valueText, MetricStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize, color = r.color))
            OneLine("${r.unit} · ${r.tag}", MaterialTheme.typography.labelMedium.copy(color = PpText2))
        } else {
            OneLine(r.needs, MaterialTheme.typography.bodyMedium.copy(color = PpText2))
        }
        Spacer(Modifier.weight(1f))
        if (r.samples.size >= 3) {
            Spacer(Modifier.height(8.dp))
            Sparkline(r.samples, r.color, Modifier.fillMaxWidth().height(22.dp))
        }
    }
}

@Composable
private fun OneLine(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun DetailPanel(r: Reading) {
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(TileShape)
            .background(PpHigh)
            .padding(14.dp),
    ) {
        Text(r.label, style = MaterialTheme.typography.titleMedium, color = PpText)
        Spacer(Modifier.height(4.dp))
        Text(r.explain, style = MaterialTheme.typography.bodyMedium, color = PpText2)
        if (r.samples.isNotEmpty()) {
            val lo = r.samples.min()
            val hi = r.samples.max()
            val fmt = "%.${r.decimals}f"
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    append("This session: ")
                    withStyle(SpanStyle(color = r.color)) {
                        append("${fmt.format(Locale.US, lo)} to ${fmt.format(Locale.US, hi)} ${r.unit}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium, color = PpText,
            )
        }
    }
}

/** Tiny line chart of the session's samples, auto-scaled to its own min-max. */
@Composable
private fun Sparkline(samples: List<Float>, color: Color, modifier: Modifier) {
    // Downsample to at most ~48 points so long sessions stay smooth and cheap to draw.
    val points = remember(samples) {
        if (samples.size <= 48) samples
        else List(48) { i -> samples[(i * (samples.size - 1)) / 47] }
    }
    Canvas(modifier) {
        val lo = points.min()
        val hi = points.max()
        val span = (hi - lo).takeIf { it > 1e-3f } ?: 1f
        val stroke = 2.dp.toPx()
        val h = size.height - stroke
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val y = stroke / 2 + h - ((v - lo) / span) * h
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * stepX, y)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val lastY = stroke / 2 + h - ((points.last() - lo) / span) * h
        drawCircle(color, stroke * 1.2f, Offset(size.width - stroke, lastY))
    }
}

// ---------------------------------------------------------------------------------------------
//  Mascot: waves once when a "Nice session!" summary opens. Tap to play a wave, then (next tap)
//  one slow breath. Taps during a play are ignored.
// ---------------------------------------------------------------------------------------------

@Composable
private fun TappableMascot(greetOnOpen: Boolean) {
    val scope = rememberCoroutineScope()
    var waving by remember { mutableStateOf(false) }
    var nextIsWave by remember { mutableStateOf(true) }
    val breath = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }

    // Celebrate a full session: one wave when the screen opens (next tap plays the slow breath).
    LaunchedEffect(Unit) {
        if (!greetOnOpen) return@LaunchedEffect
        playing = true
        nextIsWave = false
        try {
            kotlinx.coroutines.delay(300) // let the screen settle first
            waving = true
            kotlinx.coroutines.delay(1800)
        } finally {
            waving = false
            playing = false
        }
    }

    Mascot(
        mood = if (waving) MascotMood.Waving else MascotMood.Happy,
        size = 112.dp,
        breath = if (breath.isRunning) breath.value else null,
        modifier = Modifier
            .graphicsLayer {
                val s = 1f + 0.10f * breath.value
                scaleX = s; scaleY = s
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Say hi to Pause Pal",
            ) {
                if (playing) return@clickable
                playing = true
                val wave = nextIsWave
                nextIsWave = !nextIsWave
                scope.launch {
                    try {
                        if (wave) {
                            waving = true
                            kotlinx.coroutines.delay(1800)
                        } else {
                            // One slow breath: 4 s in, 4 s out.
                            breath.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
                            breath.animateTo(0f, tween(4000, easing = FastOutSlowInEasing))
                        }
                    } finally {
                        waving = false
                        playing = false
                    }
                }
            }
            .semantics { contentDescription = "Pause Pal mascot" },
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    PpCardBox(modifier, padding = 14.dp) {
        OneLine(label, MaterialTheme.typography.labelMedium.copy(color = PpText2))
        Spacer(Modifier.height(4.dp))
        OneLine(value, MetricStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize, color = PpText))
    }
}
