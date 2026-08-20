package com.appbox.runtime.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object OsColors {
    val WallpaperTop = Color(0xFF0F0C29)
    val WallpaperMid = Color(0xFF302B63)
    val WallpaperBottom = Color(0xFF24243E)

    val AccentCyan = Color(0xFF64FFDA)
    val AccentViolet = Color(0xFFBB86FC)
    val AccentPink = Color(0xFFFF6EC7)
    val AccentBlue = Color(0xFF82B1FF)

    val GlassWhite = Color(0x33FFFFFF)
    val GlassWhiteStrong = Color(0x55FFFFFF)
    val GlassBorder = Color(0x66FFFFFF)
    val GlassHighlight = Color(0x99FFFFFF)

    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xB3F5F7FF)
    val TextMuted = Color(0x80F5F7FF)

    val StatusActive = Color(0xFF69F0AE)
    val StatusSuspended = Color(0xFFFFD740)
    val StatusStopped = Color(0xFFFF5252)

    val WallpaperBrush = Brush.verticalGradient(
        colors = listOf(WallpaperTop, WallpaperMid, WallpaperBottom),
    )

    val DockBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x40FFFFFF),
            Color(0x15FFFFFF),
        ),
    )

    val CardGlow = Brush.radialGradient(
        colors = listOf(
            Color(0x3382B1FF),
            Color.Transparent,
        ),
    )
}
