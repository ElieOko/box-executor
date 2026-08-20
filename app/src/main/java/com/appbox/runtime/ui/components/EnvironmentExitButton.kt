package com.appbox.runtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.R
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun EnvironmentExitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        Row(
            modifier = modifier
                .background(AppBoxThemeColors.SurfaceElevated, RoundedCornerShape(14.dp))
                .border(1.dp, AppBoxThemeColors.Accent, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_exit_appbox),
                contentDescription = "Quitter AppBox",
                tint = AppBoxThemeColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    } else {
        Row(
            modifier = modifier
                .background(AppBoxThemeColors.SurfaceElevated, RoundedCornerShape(12.dp))
                .border(1.dp, AppBoxThemeColors.BorderStrong, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_exit_appbox),
                contentDescription = null,
                tint = AppBoxThemeColors.Accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Quitter AppBox",
                color = AppBoxThemeColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
        }
    }
}
