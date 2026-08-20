package com.appbox.runtime.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.appbox.runtime.ui.theme.OsColors

@Composable
fun OsWallpaper(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OsColors.WallpaperBrush),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            OsColors.AccentViolet.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        radius = 900f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            OsColors.AccentCyan.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        radius = 700f,
                        center = androidx.compose.ui.geometry.Offset(900f, 200f),
                    ),
                ),
        )
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderAlpha: Float = 0.35f,
    backgroundAlpha: Float = 0.12f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = backgroundAlpha + 0.08f),
                        Color.White.copy(alpha = backgroundAlpha),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha + 0.2f),
                        Color.White.copy(alpha = borderAlpha * 0.4f),
                    ),
                ),
                shape = RoundedCornerShape(cornerRadius),
            ),
        content = content,
    )
}

@Composable
fun GlassCircle(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        cornerRadius = 999.dp,
        backgroundAlpha = 0.16f,
        content = content,
    )
}

@Composable
fun AppIcon(
    packageName: String?,
    drawable: Drawable?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val painter = rememberAppIconPainter(packageName, drawable)
    GlassCircle(
        modifier = modifier
            .clip(CircleShape)
            .then(Modifier),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(0.72f)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.55f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OsColors.AccentBlue.copy(alpha = 0.35f)),
                )
            }
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
        val bitmap = source.toBitmap(width = 128, height = 128)
        BitmapPainter(bitmap.asImageBitmap())
    }
}

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
    )
}
