package com.app.pausapal.ui.screens

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.app.pausapal.presage.PresageSession
import com.app.pausapal.session.SessionViewModel
import com.app.pausapal.ui.components.Mascot
import com.app.pausapal.ui.components.MascotMood
import com.app.pausapal.ui.components.PpCardBox
import com.app.pausapal.ui.components.SecondaryButton
import com.app.pausapal.ui.components.WaveChart
import com.app.pausapal.ui.theme.MetricStyle
import com.app.pausapal.ui.theme.PpAmber
import com.app.pausapal.ui.theme.PpBg
import com.app.pausapal.ui.theme.PpBorder
import com.app.pausapal.ui.theme.PpCard
import com.app.pausapal.ui.theme.PpCoral
import com.app.pausapal.ui.theme.PpMint
import com.app.pausapal.ui.theme.PpRed
import com.app.pausapal.ui.theme.PpTeal
import com.app.pausapal.ui.theme.PpText
import com.app.pausapal.ui.theme.PpText2
import com.app.pausapal.ui.theme.TimerStyle
import java.util.Locale

@Composable
fun LiveSessionScreen(
    vm: SessionViewModel,
    previewView: PreviewView,
    onStop: () -> Unit,
    onToggleCamera: () -> Unit,
    /** Smoothed live breathing phase 0..1 from the belly/chest wave, or null with no signal. */
    breathPhase: Float?,
    /** "Voice guidance" switch changed. The caller creates / releases the TTS engine. */
    onVoiceToggle: (Boolean) -> Unit,
    /** Opens Android's screen for downloading an offline TTS voice. */
    onInstallVoice: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top row: persistent camera indicator + status chip.
        Row(verticalAlignment = Alignment.CenterVertically) {
            CameraBadge(paused = vm.cameraPaused)
            Spacer(Modifier.weight(1f))
            StatusChip(vm.status, paused = vm.cameraPaused)
        }

        // Timer card.
        // Compact timer card: mascot beside the countdown so the whole dashboard fits on one screen.
        PpCardBox(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Mascot(vm.mascotMood, size = 56.dp, breath = breathPhase)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatClock(vm.remainingMs), style = TimerStyle, color = PpText)
                    Text(
                        "Check-in · ${vm.length.minutes}m total",
                        style = MaterialTheme.typography.bodyMedium, color = PpText2,
                    )
                    Text(vm.learningHint, style = MaterialTheme.typography.bodyMedium, color = PpTeal)
                }
            }
        }

        // SDK validation hint / errors.
        vm.errorText?.let { Notice(it, PpCoral) }
        if (vm.errorText == null) vm.validationHint?.let { Notice(it, PpAmber) }

        // Even 2x2 grid: every card is half width, and cards in a row share the same height.
        MetricRow {
            MetricCard("Pulse", vm.pulse?.let { "%.0f".format(Locale.US, it) }, "bpm", "12-s average", PpCoral, it)
            MetricCard("Breathing", vm.breathing?.let { "%.0f".format(Locale.US, it) }, "/min", "30-s average", PpTeal, it)
        }
        MetricRow {
            MetricCard("HRV (RMSSD)", vm.hrv?.let { "%.0f".format(Locale.US, it) }, "ms", "needs ~60 s", PpMint, it)
            if (vm.expressionsOptIn) {
                MetricCard("Expression", vm.expression, "", "on-device estimate", PpText, it)
            } else {
                // Keeps the grid even when expressions are off.
                MetricCard("Baseline", vm.baseline?.let { "%.0f".format(Locale.US, it) }, "/min", "your normal breathing", PpTeal, it)
            }
        }

        PpCardBox(Modifier.fillMaxWidth()) {
            Text("Chest wave", style = MaterialTheme.typography.titleLarge, color = PpText)
            Spacer(Modifier.height(8.dp))
            WaveChart(vm.chestTrace, PpTeal)
        }
        PpCardBox(Modifier.fillMaxWidth()) {
            Text("Belly wave", style = MaterialTheme.typography.titleLarge, color = PpText)
            Spacer(Modifier.height(8.dp))
            WaveChart(vm.bellyTrace, PpMint)
        }

        // Camera preview: ALWAYS attached so the SDK has a surface. When hidden it is 1dp and alpha 0.
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { vm.showPreview = !vm.showPreview },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = vm.showPreview, onCheckedChange = { vm.showPreview = it },
                colors = CheckboxDefaults.colors(checkedColor = PpMint, uncheckedColor = PpBorder, checkmarkColor = PpBg),
            )
            Text("Show small camera preview", style = MaterialTheme.typography.bodyLarge, color = PpText)
        }
        // Optional haptics, OFF by default.
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Switch) { vm.hapticsOn = !vm.hapticsOn }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Gentle vibration", style = MaterialTheme.typography.bodyLarge, color = PpText)
                Text(
                    "A soft buzz when a break is suggested, light ticks while you breathe in",
                    style = MaterialTheme.typography.labelMedium, color = PpText2,
                )
            }
            Switch(
                checked = vm.hapticsOn, onCheckedChange = { vm.hapticsOn = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = PpTeal, checkedThumbColor = PpBg,
                    uncheckedTrackColor = PpBg, uncheckedThumbColor = PpText2, uncheckedBorderColor = PpBorder,
                ),
            )
        }
        // Optional voice guidance, OFF by default.
        SwitchRow(
            title = "Voice guidance",
            body = "Speaks breathing cues and camera tips. Never reads your numbers.",
            checked = vm.voiceOn,
            onChange = onVoiceToggle,
        )
        vm.voiceMessage?.let { Notice(it, PpAmber) }
        if (vm.voiceNeedsInstall) SecondaryButton("Install voice", onClick = onInstallVoice, contentColor = PpAmber)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val previewModifier = if (vm.showPreview) {
                Modifier.size(width = 150.dp, height = 200.dp).clip(RoundedCornerShape(20.dp))
                    .border(1.dp, PpBorder, RoundedCornerShape(20.dp))
            } else {
                Modifier.size(1.dp).alpha(0f)
            }
            AndroidView(
                factory = { previewView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = previewModifier,
            )
        }

        Text(
            "Tip: keep the phone steady, face and upper chest in view, even light, and avoid talking.",
            style = MaterialTheme.typography.bodyMedium, color = PpText2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(if (vm.cameraPaused) "Resume camera" else "Pause camera", onClick = onToggleCamera)
        SecondaryButton("Stop and see summary", onClick = onStop)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = PpText)
            Text(body, style = MaterialTheme.typography.labelMedium, color = PpText2)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PpTeal, checkedThumbColor = PpBg,
                uncheckedTrackColor = PpBg, uncheckedThumbColor = PpText2, uncheckedBorderColor = PpBorder,
            ),
        )
    }
}

@Composable
private fun CameraBadge(paused: Boolean) {
    Row(
        Modifier.clip(CircleShape).background(PpCard).border(1.dp, PpBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (paused) PpText2 else PpRed))
        Spacer(Modifier.width(8.dp))
        Text(if (paused) "Camera off" else "Camera active", style = MaterialTheme.typography.labelMedium, color = PpText)
    }
}

@Composable
private fun StatusChip(status: PresageSession.Status, paused: Boolean) {
    val (label, color) = if (paused) "Paused" to PpText2 else when (status) {
        PresageSession.Status.Idle, PresageSession.Status.Starting -> "Starting" to PpAmber
        PresageSession.Status.Measuring -> "Measuring" to PpMint
        PresageSession.Status.Stopping -> "Stopping" to PpText2
        PresageSession.Status.Error -> "Error" to PpCoral
    }
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun Notice(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium, color = color,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Two equal-width cards; the row is as tall as its tallest card and both cards fill that height. */
@Composable
private fun MetricRow(content: @Composable (cardModifier: Modifier) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun MetricCard(title: String, value: String?, unit: String, subtitle: String, color: Color, modifier: Modifier) {
    PpCardBox(modifier, padding = 16.dp) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = PpText2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: "--", style = MetricStyle, color = if (value == null) PpText2 else color,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.titleMedium, color = color.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = PpText2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun formatClock(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%02d:%02d".format(Locale.US, total / 60, total % 60)
}
