package com.app.pausapal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** User's theme choice. Held in memory only (the app persists nothing), so it resets to Auto. */
enum class ThemeMode(val label: String) { System("Auto"), Light("Light"), Dark("Dark") }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun PausaPalTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.teal, onPrimary = p.onTeal, secondary = p.coral, tertiary = p.mint,
            background = p.bg, onBackground = p.text, surface = p.card, onSurface = p.text,
            surfaceVariant = p.high, onSurfaceVariant = p.text2, outline = p.border, outlineVariant = p.border,
        )
    } else {
        lightColorScheme(
            primary = p.teal, onPrimary = p.onTeal, secondary = p.coral, tertiary = p.mint,
            background = p.bg, onBackground = p.text, surface = p.card, onSurface = p.text,
            surfaceVariant = p.high, onSurfaceVariant = p.text2, outline = p.border, outlineVariant = p.border,
        )
    }
    CompositionLocalProvider(LocalPpPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
