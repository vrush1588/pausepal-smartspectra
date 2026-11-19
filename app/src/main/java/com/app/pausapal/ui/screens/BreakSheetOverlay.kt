package com.app.pausapal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.pausapal.session.BreakSheet
import com.app.pausapal.session.BreakTips
import com.app.pausapal.ui.components.Mascot
import com.app.pausapal.ui.components.MascotMood
import com.app.pausapal.ui.components.PrimaryButton
import com.app.pausapal.ui.components.SecondaryButton
import com.app.pausapal.ui.theme.BrandTeal
import com.app.pausapal.ui.theme.MascotInk
import com.app.pausapal.ui.theme.PpBg
import com.app.pausapal.ui.theme.PpBorder
import com.app.pausapal.ui.theme.PpCard
import com.app.pausapal.ui.theme.PpHigh
import com.app.pausapal.ui.theme.PpMint
import com.app.pausapal.ui.theme.PpTeal
import com.app.pausapal.ui.theme.PpText
import com.app.pausapal.ui.theme.PpText2
import java.util.Locale

/** Bottom sheet over the (dimmed) live screen: break suggestion, then 60 s guided breathing. */
@Composable
fun BreakSheetOverlay(
    sheet: BreakSheet,
    snoozeLabel: String,
    onTakeBreak: () -> Unit,
    onNotNow: () -> Unit,
    onImBack: () -> Unit,
    /** The mascot's own mood (from breathing vs the user's baseline). */
    mascotMood: MascotMood,
    /** Smoothed live breathing phase 0..1, or null when there is no wave signal. */
    breathPhase: Float?,
    onToggleStep: (Int) -> Unit,
    /** Called at the start of each guided inhale during the 60 s reset (haptic tick, voice cue). */
    onGuideInhale: () -> Unit,
    /** Called at the start of each guided exhale during the 60 s reset (voice cue). */
    onGuideExhale: () -> Unit = {},
) {
    val visible = sheet != BreakSheet.Hidden
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                // Swallow taps on the dimmed area so the live screen behind is not touched.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(350)) { it },
            exit = slideOutVertically(tween(250)) { it },
        ) {
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(PpHigh).border(1.dp, PpBorder, shape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(PpBorder))
                Spacer(Modifier.height(10.dp))

                when (sheet) {
                    is BreakSheet.Breathing -> {
                        val steps = BreakTips.steps
                        val allTicked = steps.isNotEmpty() && sheet.ticked.size == steps.size
                        Mascot(
                            if (allTicked) MascotMood.Waving else mascotMood,
                            size = 92.dp, breath = breathPhase,
                        )
                        if (allTicked) {
                            Text("Nice pause!", style = MaterialTheme.typography.titleLarge, color = PpTeal)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text("Breathe with Pause Pal", style = MaterialTheme.typography.headlineSmall, color = PpText, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (!sheet.finished) "${sheet.secondsLeft} s left" else "Break done. Welcome back.",
                            style = MaterialTheme.typography.bodyLarge, color = PpText2,
                        )
                        Spacer(Modifier.height(14.dp))
                        BreathingCircle(
                            livePhase = breathPhase,
                            onInhale = if (!sheet.finished) onGuideInhale else null,
                            onExhale = if (!sheet.finished) onGuideExhale else null,
                        )
                        if (sheet.finished) {
                            Spacer(Modifier.height(12.dp))
                            BeforeAfter(sheet.before, sheet.after)
                        }
                        Spacer(Modifier.height(12.dp))
                        ChosenTip(sheet.tip)
                        Spacer(Modifier.height(12.dp))
                        BreakChecklist(steps, sheet.ticked, onToggleStep)
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton("I'm back", onClick = onImBack)
                    }
                    else -> {
                        Mascot(MascotMood.Waving, size = 92.dp, breath = breathPhase)
                        Text("You might want a short break", style = MaterialTheme.typography.headlineSmall, color = PpText, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "A quiet pause helps your mind stay clear and refreshed.",
                            style = MaterialTheme.typography.bodyMedium, color = PpText2, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        TipCard()
                        Spacer(Modifier.height(14.dp))
                        BreathingCircle(livePhase = breathPhase)
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton("Take a break", onClick = onTakeBreak)
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton(snoozeLabel, onClick = onNotNow)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Suggested because your breathing has been above your own baseline for a little while.",
                            style = MaterialTheme.typography.labelMedium, color = PpText2, textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun TipCard() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PpCard)
            .border(1.dp, PpBorder, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text("Break tips", style = MaterialTheme.typography.labelLarge, color = PpTeal)
        Spacer(Modifier.height(6.dp))
        BreakTips.tips.forEach { tip ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•", color = PpTeal, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Text(tip, style = MaterialTheme.typography.bodyMedium, color = PpText)
            }
        }
    }
}

/** The one break tip picked for this break. */
@Composable
private fun ChosenTip(tip: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PpCard)
            .border(1.dp, PpBorder, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text("Your break tip", style = MaterialTheme.typography.labelLarge, color = PpTeal)
        Spacer(Modifier.height(4.dp))
        Text(tip, style = MaterialTheme.typography.bodyLarge, color = PpText)
    }
}

/**
 * Before / after breathing rate for the 60 s reset. Neutral wording and colours only: no
 * good/bad labels. Breathing rate is a ~30 s average, so "after" lags behind.
 */
@Composable
private fun BeforeAfter(before: Float?, after: Float?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PpCard)
            .border(1.dp, PpBorder, RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (before != null && after != null) {
                "Before ${"%.0f".format(Locale.US, before)} /min, after ${"%.0f".format(Locale.US, after)} /min"
            } else {
                "Before and after: not enough data yet"
            },
            style = MaterialTheme.typography.titleMedium, color = PpText, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Breathing rate is a 30-second average, so it updates gradually.",
            style = MaterialTheme.typography.labelMedium, color = PpText2, textAlign = TextAlign.Center,
        )
    }
}

/** 2–3 tap-to-tick break steps for every session. */
@Composable
private fun BreakChecklist(steps: List<String>, ticked: Set<Int>, onToggle: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PpCard)
            .border(1.dp, PpBorder, RoundedCornerShape(20.dp)).padding(vertical = 6.dp),
    ) {
        Text(
            "Break steps", style = MaterialTheme.typography.labelLarge, color = PpTeal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        steps.forEachIndexed { i, step ->
            val checked = i in ticked
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onToggle(i) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked, onCheckedChange = { onToggle(i) },
                    colors = CheckboxDefaults.colors(checkedColor = PpMint, uncheckedColor = PpBorder, checkmarkColor = PpBg),
                )
                Text(step, style = MaterialTheme.typography.bodyLarge, color = if (checked) PpText2 else PpText)
            }
        }
    }
}

/**
 * Breathing circle that follows the user.
 * - Filled circle: scale follows the live chest/belly wave ([livePhase], normalised to its recent
 *   min/max). With no wave data it falls back to the fixed rhythm.
 * - Faint ring: fixed-rhythm guide, 4.5 s in / 4.5 s out. The text prompt follows the ring.
 * @param onInhale called at the start of each guide inhale (null = no callback)
 * @param onExhale called at the start of each guide exhale (null = no callback)
 */
@Composable
fun BreathingCircle(livePhase: Float? = null, onInhale: (() -> Unit)? = null, onExhale: (() -> Unit)? = null) {
    val guide = remember { Animatable(0f) }
    var inhaling by remember { mutableStateOf(true) }
    val inhaleCallback by rememberUpdatedState(onInhale)
    val exhaleCallback by rememberUpdatedState(onExhale)
    LaunchedEffect(Unit) {
        while (true) {
            inhaling = true
            inhaleCallback?.invoke()
            guide.animateTo(1f, tween(4500, easing = FastOutSlowInEasing))
            inhaling = false
            exhaleCallback?.invoke()
            guide.animateTo(0f, tween(4500, easing = FastOutSlowInEasing))
        }
    }
    val following = livePhase != null
    val ringColor = PpText.copy(alpha = 0.35f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).background(PpBg),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(180.dp)) {
                val r = size.minDimension / 2
                val maxR = r * 0.62f
                val minR = maxR * 0.62f
                val userR = minR + (maxR - minR) * (livePhase ?: guide.value)
                val guideR = minR + (maxR - minR) * guide.value
                drawCircle(
                    Brush.radialGradient(listOf(BrandTeal.copy(alpha = 0.22f), Color.Transparent), radius = r),
                    radius = r,
                )
                drawCircle(BrandTeal.copy(alpha = 0.18f), radius = userR * (0.82f / 0.62f))
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF8AF0E9), BrandTeal), radius = userR),
                    radius = userR,
                )
                // Faint fixed-rhythm guide ring: the target to breathe along with.
                if (following) {
                    drawCircle(
                        ringColor, radius = guideR * (0.82f / 0.62f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            // Fixed dark ink: the circle centre is light teal in both themes.
            Text(
                if (inhaling) "Breathe in…" else "Breathe out slowly…",
                style = MaterialTheme.typography.titleMedium, color = MascotInk, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (following) "The circle follows your breathing. The ring is the guide." else "Follow the circle.",
            style = MaterialTheme.typography.labelMedium, color = PpText2, textAlign = TextAlign.Center,
        )
    }
}
