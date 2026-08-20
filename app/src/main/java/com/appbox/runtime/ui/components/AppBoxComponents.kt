package com.appbox.runtime.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun AppBoxBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBoxThemeColors.Background),
    )
}

@Composable
fun AppBoxPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val bg = if (elevated) AppBoxThemeColors.SurfaceElevated else AppBoxThemeColors.Surface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg)
            .border(1.dp, AppBoxThemeColors.Border, RoundedCornerShape(cornerRadius)),
        content = content,
    )
}

@Composable
fun AppIcon(
    packageName: String?,
    drawable: Drawable?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val painter = rememberAppIconPainter(packageName, drawable)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(AppBoxThemeColors.SurfaceElevated)
            .border(1.dp, AppBoxThemeColors.Border, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(0.72f)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.4f)
                    .clip(CircleShape)
                    .background(AppBoxThemeColors.AccentSoft),
            )
        }
    }
}

@Composable
fun rememberAppIconPainter(packageName: String?, drawable: Drawable?): Painter? {
    val context = LocalContext.current
    return remember(packageName, drawable) {
        val source = drawable ?: packageName?.let {
            runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull()
        } ?: return@remember null
        BitmapPainter(source.toBitmap(width = 128, height = 128).asImageBitmap())
    }
}

@Composable
fun StatusIndicator(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) AppBoxThemeColors.Success else AppBoxThemeColors.TextTertiary)
            .border(1.dp, AppBoxThemeColors.BorderStrong, CircleShape),
    )
}
