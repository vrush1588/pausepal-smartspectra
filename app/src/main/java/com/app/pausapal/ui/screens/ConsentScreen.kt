package com.app.pausapal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.pausapal.session.SessionViewModel
import com.app.pausapal.ui.components.Glyph
import com.app.pausapal.ui.components.GlyphBadge
import com.app.pausapal.ui.components.GlyphIcon
import com.app.pausapal.ui.components.Mascot
import com.app.pausapal.ui.components.MascotMood
import com.app.pausapal.ui.components.Pill
import com.app.pausapal.ui.components.PpCardBox
import com.app.pausapal.ui.components.PrimaryButton
import com.app.pausapal.ui.components.ThemeToggle
import com.app.pausapal.ui.theme.PpBg
import com.app.pausapal.ui.theme.PpBorder
import com.app.pausapal.ui.theme.PpMint
import com.app.pausapal.ui.theme.PpTeal
import com.app.pausapal.ui.theme.PpText
import com.app.pausapal.ui.theme.PpText2

private data class Bullet(val glyph: Glyph, val title: String, val body: String)

// Exact consent wording (see README "Consent and Data Handling").
private val bullets = listOf(
    Bullet(Glyph.Phone, "Processed on this phone", "Video frames are analysed on the device."),
    Bullet(Glyph.NoVideo, "No video or photo saved", "This app never records or stores camera images."),
    Bullet(Glyph.Memory, "Numbers stay in memory only", "Readings are erased when you leave the summary or close the app."),
    Bullet(
        Glyph.Globe, "Needs internet",
        "The SmartSpectra SDK contacts Presage's servers to check the API key and load models. " +
            "Video frames are not uploaded by this app.",
    ),
    Bullet(Glyph.Leaf, "Wellness only", "Not a medical device, no diagnosis or medical advice."),
    Bullet(Glyph.NoAi, "No AI service", "No AI service is used by this app."),
)

@Composable
fun ConsentScreen(vm: SessionViewModel, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThemeToggle(vm.themeMode, onChange = { vm.themeMode = it }, modifier = Modifier.align(Alignment.End))
        Mascot(MascotMood.Calm, size = 104.dp)
        Text("Pause Pal", style = MaterialTheme.typography.titleLarge, color = PpText)
        Spacer(Modifier.height(6.dp))
        Text(
            "A buddy that notices when you might want a break.",
            style = MaterialTheme.typography.bodyLarge, color = PpText2, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Pill("On-Device & Private", PpTeal, leading = { GlyphIcon(Glyph.Lock, PpTeal, 14.dp) })
        Spacer(Modifier.height(20.dp))

        PpCardBox(Modifier.fillMaxWidth(), padding = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphBadge(Glyph.Shield, PpTeal, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text("How your camera is used", style = MaterialTheme.typography.titleLarge, color = PpText)
            }
            Spacer(Modifier.height(16.dp))
            bullets.forEach { b ->
                Row(Modifier.padding(vertical = 7.dp)) {
                    GlyphBadge(b.glyph, PpTeal, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(b.title, style = MaterialTheme.typography.titleMedium, color = PpText)
                        Text(b.body, style = MaterialTheme.typography.bodyMedium, color = PpText2)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Required consent checkbox.
        PpCardBox(
            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { vm.consentGiven = !vm.consentGiven },
            borderColor = if (vm.consentGiven) PpTeal.copy(alpha = 0.5f) else PpBorder,
            padding = 14.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = vm.consentGiven,
                    onCheckedChange = { vm.consentGiven = it },
                    colors = CheckboxDefaults.colors(checkedColor = PpMint, uncheckedColor = PpBorder, checkmarkColor = PpBg),
                )
                Text(
                    "I agree to use my camera for this session",
                    style = MaterialTheme.typography.titleMedium, color = PpText, modifier = Modifier.weight(1f),
                )
                Text(
                    "Required", style = MaterialTheme.typography.labelMedium, color = PpTeal,
                    modifier = Modifier.clip(CircleShape).background(PpBg).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OptionalSwitch(
            title = "Analyse facial expressions (optional)",
            body = "Adds an on-device expression estimate to the live screen. Off by default.",
            checked = vm.expressionsOptIn,
            onChange = { vm.expressionsOptIn = it },
        )
        Spacer(Modifier.height(12.dp))
        OptionalSwitch(
            title = "Allow anonymous SDK diagnostics (aggregate performance data, no video, no vitals)",
            body = "Off by default.",
            checked = vm.telemetryOptIn,
            onChange = { vm.telemetryOptIn = it },
        )

        Spacer(Modifier.height(24.dp))
        PrimaryButton("Continue  →", onClick = onContinue, enabled = vm.consentGiven)
        Spacer(Modifier.height(12.dp))
        Text(
            "You can revoke camera permission anytime in Android settings.",
            style = MaterialTheme.typography.bodyMedium, color = PpText2, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OptionalSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    PpCardBox(Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = PpText)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = PpText2)
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
}
