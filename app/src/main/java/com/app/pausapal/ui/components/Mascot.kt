package com.app.pausapal.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.pausapal.ui.theme.MascotInk
import com.app.pausapal.ui.theme.BrandTeal

/**
 * The mascot's own mood (never a statement about the user).
 * Neutral: before a baseline exists. Calm: near the user's baseline. Lively: above it.
 * Waving / Happy: greetings and celebrations.
 */
enum class MascotMood { Neutral, Calm, Lively, Waving, Happy }

/**
 * Pause Pal mascot, drawn entirely in code (no image assets / URLs):
 * a round teal blob with a soft glow, two dark dot eyes and a small smile.
 *
 * @param breath live breathing phase 0..1 (already smoothed), e.g. from the belly wave. The body
 *   and glow grow on inhale so the mascot breathes with the user. Null = gentle 4 s loop.
 */
@Composable
fun Mascot(mood: MascotMood, size: Dp = 112.dp, modifier: Modifier = Modifier, breath: Float? = null) {
    val t = rememberInfiniteTransition(label = "mascot")
    val loop by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val breathNow = breath ?: loop
    // Animate between moods so expressions blend instead of snapping.
    val lively by animateFloatAsState(if (mood == MascotMood.Lively) 1f else 0f, label = "lively")
    val neutral by animateFloatAsState(if (mood == MascotMood.Neutral) 1f else 0f, label = "neutral")
    val wave by t.animateFloat(
        initialValue = -18f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave",
    )

    Canvas(modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2f, w / 2f + w * 0.04f)
        val r = w * 0.30f
        val sx = 1f + 0.05f * breathNow
        val sy = 1f + 0.035f * breathNow

        // Soft bioluminescent glow: swells on inhale, a little brighter when lively, dimmer when neutral.
        val glowR = w * (0.44f + 0.06f * breathNow)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    BrandTeal.copy(alpha = 0.10f + 0.12f * breathNow + 0.06f * lively - 0.04f * neutral),
                    Color.Transparent,
                ),
                center = c, radius = glowR,
            ),
            radius = glowR, center = c,
        )

        // Waving arm (behind body).
        if (mood == MascotMood.Waving) {
            rotate(wave, pivot = Offset(c.x + r * 0.75f, c.y + r * 0.1f)) {
                drawLine(
                    BrandTeal, Offset(c.x + r * 0.75f, c.y + r * 0.1f),
                    Offset(c.x + r * 1.35f, c.y - r * 0.55f),
                    strokeWidth = r * 0.28f, cap = StrokeCap.Round,
                )
            }
        }

        // Blob body: slightly squashed oval with a lighter top highlight.
        val bw = r * 2f * sx
        val bh = r * 1.85f * sy
        drawOval(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF7DE3DC), BrandTeal, Color(0xFF3BB3AB)),
                startY = c.y - bh / 2, endY = c.y + bh / 2,
            ),
            topLeft = Offset(c.x - bw / 2, c.y - bh / 2),
            size = Size(bw, bh),
        )
        drawOval(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(c.x - bw * 0.28f, c.y - bh * 0.42f),
            size = Size(bw * 0.3f, bh * 0.16f),
        )

        // Eyes.
        val eyeY = c.y - r * 0.12f
        val eyeDx = r * 0.36f
        val eyeR = r * (0.11f + 0.015f * lively)
        if (mood == MascotMood.Happy) {
            for (dx in listOf(-eyeDx, eyeDx)) {
                drawArc(
                    MascotInk, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(c.x + dx - eyeR * 1.3f, eyeY - eyeR),
                    size = Size(eyeR * 2.6f, eyeR * 2.4f),
                    style = Stroke(width = eyeR * 0.75f, cap = StrokeCap.Round),
                )
            }
        } else {
            drawCircle(MascotInk, eyeR, Offset(c.x - eyeDx, eyeY))
            drawCircle(MascotInk, eyeR, Offset(c.x + eyeDx, eyeY))
            drawCircle(Color.White.copy(alpha = 0.7f), eyeR * 0.32f, Offset(c.x - eyeDx + eyeR * 0.35f, eyeY - eyeR * 0.35f))
            drawCircle(Color.White.copy(alpha = 0.7f), eyeR * 0.32f, Offset(c.x + eyeDx + eyeR * 0.35f, eyeY - eyeR * 0.35f))
        }

        // Smile: small and soft when neutral, calm curve, wider when lively / happy / waving.
        val greeting = mood == MascotMood.Waving || mood == MascotMood.Happy
        val smileW = if (greeting) r * 0.6f else r * (0.42f + 0.14f * lively - 0.06f * neutral)
        val smileH = smileW * (0.7f - 0.4f * neutral)
        drawArc(
            MascotInk, startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(c.x - smileW / 2, eyeY + r * 0.02f + (smileW * 0.7f - smileH) / 2),
            size = Size(smileW, smileH),
            style = Stroke(width = r * 0.08f, cap = StrokeCap.Round),
        )

        // Cheeks.
        drawCircle(Color(0xFFFF8A8A).copy(alpha = 0.35f), r * 0.1f, Offset(c.x - r * 0.62f, eyeY + r * 0.28f))
        drawCircle(Color(0xFFFF8A8A).copy(alpha = 0.35f), r * 0.1f, Offset(c.x + r * 0.62f, eyeY + r * 0.28f))
    }
}
