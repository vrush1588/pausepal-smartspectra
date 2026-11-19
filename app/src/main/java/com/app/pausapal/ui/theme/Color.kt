package com.app.pausapal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** One set of design tokens. Dark = "Nocturne Serenity" from DESIGN.md; Light = its daytime twin. */
@Immutable
data class PpPalette(
    val bg: Color,
    val card: Color,
    val high: Color,
    val border: Color,
    val text: Color,
    val text2: Color,
    val teal: Color,
    val coral: Color,
    val mint: Color,
    val amber: Color,
    val onTeal: Color,
    val red: Color,
)

// Tokens from stitch_pause_pal_wellness_companion/nocturne_serenity/DESIGN.md
val DarkPalette = PpPalette(
    bg = Color(0xFF0B1020),
    card = Color(0xFF151D36),
    high = Color(0xFF1E294B),
    border = Color(0xFF232E52),
    text = Color(0xFFF1F5F9),
    text2 = Color(0xFF94A3B8),
    teal = Color(0xFF4FCCC4),
    coral = Color(0xFFFF6B6B),
    mint = Color(0xFF6EE7B7),
    amber = Color(0xFFFBBF24),
    onTeal = Color(0xFF0B1020),
    red = Color(0xFFEF4444),
)

// Same hues, deepened so text and icons keep readable contrast on white surfaces.
val LightPalette = PpPalette(
    bg = Color(0xFFF4F7FB),
    card = Color(0xFFFFFFFF),
    high = Color(0xFFF9FBFE),
    border = Color(0xFFDCE3EE),
    text = Color(0xFF0F172A),
    text2 = Color(0xFF475569),
    teal = Color(0xFF0E8A82),
    coral = Color(0xFFD64545),
    mint = Color(0xFF047857),
    amber = Color(0xFFB45309),
    onTeal = Color(0xFFFFFFFF),
    red = Color(0xFFDC2626),
)

val LocalPpPalette = staticCompositionLocalOf { DarkPalette }

// Theme-aware tokens: read from the current palette, so every screen follows Light / Dark.
val PpBg: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.bg
val PpCard: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.card
val PpHigh: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.high
val PpBorder: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.border
val PpText: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.text
val PpText2: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.text2
val PpTeal: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.teal
val PpCoral: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.coral
val PpMint: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.mint
val PpAmber: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.amber
val PpOnTeal: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.onTeal
val PpRed: Color @Composable @ReadOnlyComposable get() = LocalPpPalette.current.red

// Fixed brand colours: the mascot and breathing circle look the same in both themes.
val BrandTeal = Color(0xFF4FCCC4)
val MascotInk = Color(0xFF0B1020)
