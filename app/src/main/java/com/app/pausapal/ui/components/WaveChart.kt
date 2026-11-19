package com.app.pausapal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.app.pausapal.ui.theme.PpBorder
import com.app.pausapal.ui.theme.PpText2

/** Live line chart for a breathing trace, auto-scaled to the buffered window. */
@Composable
fun WaveChart(samples: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val border = PpBorder
    Box(modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        if (samples.size < 2) {
            Text("Waiting for signal…", style = MaterialTheme.typography.bodyMedium, color = PpText2)
            Canvas(Modifier.matchParentSize()) {
                val y = size.height / 2
                drawLine(border, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            }
            return@Box
        }
        Canvas(Modifier.matchParentSize()) {
            var min = Float.MAX_VALUE
            var max = -Float.MAX_VALUE
            for (v in samples) { if (v < min) min = v; if (v > max) max = v }
            val range = (max - min).takeIf { it > 1e-6f } ?: 1f
            val pad = size.height * 0.12f
            val h = size.height - pad * 2
            val step = size.width / (samples.size - 1)
            val path = Path()
            samples.forEachIndexed { i, v ->
                val x = i * step
                val y = pad + h - (v - min) / range * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color.copy(alpha = 0.18f), style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
