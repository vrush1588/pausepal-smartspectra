package com.app.pausapal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import com.app.pausapal.ui.theme.ThemeMode
import com.app.pausapal.ui.theme.PpBorder
import com.app.pausapal.ui.theme.PpCard
import com.app.pausapal.ui.theme.PpOnTeal
import com.app.pausapal.ui.theme.PpTeal
import com.app.pausapal.ui.theme.PpText
import com.app.pausapal.ui.theme.PpText2

val CardShape = RoundedCornerShape(24.dp)

/** Card: #151D36, 24dp radius, 1dp #232E52 border. */
@Composable
fun PpCardBox(
    modifier: Modifier = Modifier,
    borderColor: Color = PpBorder,
    background: Color = PpCard,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(background)
            .border(1.dp, borderColor, CardShape)
            .padding(padding),
        content = content,
    )
}

/** Primary pill: 52dp, teal fill, dark text. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = PpTeal, contentColor = PpOnTeal,
            disabledContainerColor = PpTeal.copy(alpha = 0.25f), disabledContentColor = PpOnTeal.copy(alpha = 0.6f),
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Secondary pill: transparent, 1.5dp border, light text. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, contentColor: Color = PpText) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, PpBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.height(44.dp)) {
        Text(text, color = PpText2, style = MaterialTheme.typography.labelLarge)
    }
}

/** Small pill label (e.g. "On-Device & Private"). */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .background(PpCard)
            .border(1.dp, PpBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Compact Auto / Light / Dark segmented switch. */
@Composable
fun ThemeToggle(mode: ThemeMode, onChange: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(PpCard)
            .border(1.dp, PpBorder, CircleShape)
            .padding(3.dp),
    ) {
        ThemeMode.entries.forEach { m ->
            val selected = m == mode
            Text(
                m.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) PpOnTeal else PpText2,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) PpTeal else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { onChange(m) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Tinted round badge holding a tiny code-drawn glyph. */
@Composable
fun GlyphBadge(glyph: Glyph, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, tint, size * 0.5f) }
}

enum class Glyph { Phone, NoVideo, Memory, Globe, Leaf, NoAi, Lock, Shield }

/** Minimal line icons drawn on Canvas, so no icon library or image asset is needed. */
@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val st = Stroke(width = s * 0.11f, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.Phone -> {
                rr(tint, Offset(s * 0.28f, s * 0.08f), Size(s * 0.44f, s * 0.84f), s * 0.1f, st)
                drawLine(tint, Offset(s * 0.44f, s * 0.78f), Offset(s * 0.56f, s * 0.78f), st.width, StrokeCap.Round)
            }
            Glyph.NoVideo -> {
                rr(tint, Offset(s * 0.08f, s * 0.28f), Size(s * 0.6f, s * 0.44f), s * 0.08f, st)
                drawLine(tint, Offset(s * 0.7f, s * 0.42f), Offset(s * 0.92f, s * 0.3f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.7f, s * 0.58f), Offset(s * 0.92f, s * 0.7f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.1f, s * 0.1f), Offset(s * 0.9f, s * 0.9f), st.width, StrokeCap.Round)
            }
            Glyph.Memory -> {
                rr(tint, Offset(s * 0.22f, s * 0.22f), Size(s * 0.56f, s * 0.56f), s * 0.08f, st)
                for (i in 0..2) {
                    val p = s * (0.34f + i * 0.16f)
                    drawLine(tint, Offset(p, s * 0.04f), Offset(p, s * 0.18f), st.width * 0.8f, StrokeCap.Round)
                    drawLine(tint, Offset(p, s * 0.82f), Offset(p, s * 0.96f), st.width * 0.8f, StrokeCap.Round)
                }
            }
            Glyph.Globe -> {
                drawCircle(tint, s * 0.4f, style = st)
                drawOval(tint, Offset(s * 0.3f, s * 0.1f), Size(s * 0.4f, s * 0.8f), style = st)
                drawLine(tint, Offset(s * 0.1f, s * 0.5f), Offset(s * 0.9f, s * 0.5f), st.width, StrokeCap.Round)
            }
            Glyph.Leaf -> {
                val p = Path().apply {
                    moveTo(s * 0.2f, s * 0.8f)
                    cubicTo(s * 0.1f, s * 0.3f, s * 0.5f, s * 0.1f, s * 0.85f, s * 0.15f)
                    cubicTo(s * 0.9f, s * 0.5f, s * 0.7f, s * 0.9f, s * 0.2f, s * 0.8f)
                }
                drawPath(p, tint, style = st)
                drawLine(tint, Offset(s * 0.2f, s * 0.8f), Offset(s * 0.6f, s * 0.4f), st.width, StrokeCap.Round)
            }
            Glyph.NoAi -> {
                val p = Path().apply {
                    moveTo(s * 0.5f, s * 0.12f); lineTo(s * 0.6f, s * 0.4f); lineTo(s * 0.88f, s * 0.5f)
                    lineTo(s * 0.6f, s * 0.6f); lineTo(s * 0.5f, s * 0.88f); lineTo(s * 0.4f, s * 0.6f)
                    lineTo(s * 0.12f, s * 0.5f); lineTo(s * 0.4f, s * 0.4f); close()
                }
                drawPath(p, tint, style = st)
                drawLine(tint, Offset(s * 0.1f, s * 0.1f), Offset(s * 0.9f, s * 0.9f), st.width, StrokeCap.Round)
            }
            Glyph.Lock -> {
                rr(tint, Offset(s * 0.2f, s * 0.45f), Size(s * 0.6f, s * 0.45f), s * 0.08f, st)
                drawArc(tint, 180f, 180f, false, Offset(s * 0.32f, s * 0.12f), Size(s * 0.36f, s * 0.6f), style = st)
            }
            Glyph.Shield -> {
                val p = Path().apply {
                    moveTo(s * 0.5f, s * 0.08f); lineTo(s * 0.85f, s * 0.22f)
                    cubicTo(s * 0.85f, s * 0.6f, s * 0.7f, s * 0.82f, s * 0.5f, s * 0.92f)
                    cubicTo(s * 0.3f, s * 0.82f, s * 0.15f, s * 0.6f, s * 0.15f, s * 0.22f); close()
                }
                drawPath(p, tint, style = st)
                drawLine(tint, Offset(s * 0.36f, s * 0.5f), Offset(s * 0.47f, s * 0.61f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.47f, s * 0.61f), Offset(s * 0.66f, s * 0.4f), st.width, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.rr(color: Color, topLeft: Offset, size: Size, radius: Float, style: Stroke) =
    drawRoundRect(color, topLeft, size, CornerRadius(radius, radius), style = style)
