package com.appbox.runtime.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Scheme = darkColorScheme(
    primary = AppBoxThemeColors.Accent,
    onPrimary = Color.White,
    background = AppBoxThemeColors.Background,
    onBackground = AppBoxThemeColors.TextPrimary,
    surface = AppBoxThemeColors.Surface,
    onSurface = AppBoxThemeColors.TextPrimary,
    surfaceVariant = AppBoxThemeColors.SurfaceElevated,
    onSurfaceVariant = AppBoxThemeColors.TextSecondary,
    outline = AppBoxThemeColors.Border,
    error = AppBoxThemeColors.Error,
)

@Composable
fun AppBoxTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography,
        content = content,
    )
}
