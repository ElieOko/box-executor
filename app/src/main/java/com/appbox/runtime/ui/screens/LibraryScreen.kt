package com.appbox.runtime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.components.AppIcon
import com.appbox.runtime.ui.components.StatusIndicator
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun LibraryScreen(
    apps: List<AppBoxApp>,
    onLaunchApp: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onAddApp: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${apps.size} application(s)",
                color = AppBoxThemeColors.TextSecondary,
                fontSize = 14.sp,
            )
            AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.clickable(onClick = onAddApp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ajouter", color = AppBoxThemeColors.Accent, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 20.dp) {
            if (apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune application", color = AppBoxThemeColors.TextSecondary)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        LibraryRow(
                            app = app,
                            onLaunch = { onLaunchApp(app.packageName) },
                            onRemove = { onRemoveApp(app.packageName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(app: AppBoxApp, onLaunch: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, drawable = null, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.displayName, color = AppBoxThemeColors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, color = AppBoxThemeColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIndicator(active = app.state == AppLifecycleState.ACTIVE)
                Spacer(modifier = Modifier.width(6.dp))
                Text(app.state.name, color = AppBoxThemeColors.TextSecondary, fontSize = 11.sp)
            }
        }
        IconButton(onClick = onLaunch) {
            Icon(Icons.Default.PlayArrow, "Lancer", tint = AppBoxThemeColors.Accent)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, "Retirer", tint = AppBoxThemeColors.Error)
        }
    }
}
