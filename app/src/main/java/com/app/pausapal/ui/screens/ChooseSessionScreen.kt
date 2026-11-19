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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.pausapal.session.SessionLength
import com.app.pausapal.session.SessionViewModel
import com.app.pausapal.ui.components.Glyph
import com.app.pausapal.ui.components.GlyphIcon
import com.app.pausapal.ui.components.Mascot
import com.app.pausapal.ui.components.MascotMood
import com.app.pausapal.ui.components.PpCardBox
import com.app.pausapal.ui.components.PrimaryButton
import com.app.pausapal.ui.components.ThemeToggle
import com.app.pausapal.ui.theme.PpAmber
import com.app.pausapal.ui.theme.PpBg
import com.app.pausapal.ui.theme.PpBorder
import com.app.pausapal.ui.theme.PpCard
import com.app.pausapal.ui.theme.PpTeal
import com.app.pausapal.ui.theme.PpText
import com.app.pausapal.ui.theme.PpText2

@Composable
fun ChooseSessionScreen(vm: SessionViewModel, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        ThemeToggle(vm.themeMode, onChange = { vm.themeMode = it }, modifier = Modifier.align(Alignment.End))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Choose your check-in length", style = MaterialTheme.typography.headlineMedium, color = PpText)
            }
            Mascot(MascotMood.Calm, size = 84.dp)
        }
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SessionLength.entries.forEach { l ->
                LengthChip(l, selected = vm.length == l, onClick = { vm.length = l }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Short check-ins work best: keep your phone steady with your face in view.",
            style = MaterialTheme.typography.bodyMedium, color = PpText2,
        )

        Spacer(Modifier.height(20.dp))
        PpCardBox(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Lock, PpTeal, 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "The front camera turns on only after you tap Start session, and stops when the session ends or you leave the app.",
                    style = MaterialTheme.typography.bodyMedium, color = PpText2,
                )
            }
        }

        vm.permissionMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = PpAmber)
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton("Start session", onClick = onStart)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LengthChip(l: SessionLength, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) PpTeal else PpCard)
            .border(1.dp, if (selected) PpTeal else PpBorder, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(l.label, style = MaterialTheme.typography.titleMedium, color = if (selected) PpBg else PpText, textAlign = TextAlign.Center)
        Text(
            l.sub, style = MaterialTheme.typography.labelMedium,
            color = if (selected) PpBg.copy(alpha = 0.75f) else PpText2,
        )
    }
}
