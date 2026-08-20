package com.appbox.runtime.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OsDarkScheme = darkColorScheme(
    primary = OsColors.AccentCyan,
    secondary = OsColors.AccentViolet,
    tertiary = OsColors.AccentPink,
    background = OsColors.WallpaperBottom,
    surface = OsColors.GlassWhite,
    onPrimary = Color(0xFF001F24),
    onSecondary = Color.White,
    onBackground = OsColors.TextPrimary,
    onSurface = OsColors.TextPrimary,
    surfaceVariant = OsColors.GlassWhiteStrong,
    outline = OsColors.GlassBorder,
)

private val OsLightScheme = lightColorScheme(
    primary = Color(0xFF006874),
    secondary = Color(0xFF6750A4),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFF4F6FB),
    surface = Color(0xCCFFFFFF),
    onBackground = Color(0xFF1A1C2E),
    onSurface = Color(0xFF1A1C2E),
)

@Composable
fun AppBoxTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) OsDarkScheme else OsLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
