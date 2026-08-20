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
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.ui.components.AppIcon
import com.appbox.runtime.ui.components.GlassSurface
import com.appbox.runtime.ui.components.StatusDot
import com.appbox.runtime.ui.theme.OsColors

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
            Column {
                Text(
                    text = "Bibliothèque",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OsColors.TextPrimary,
                )
                Text(
                    text = "${apps.size} application(s) dans l'écosystème",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OsColors.TextSecondary,
                )
            }
            GlassSurface(cornerRadius = 16.dp, modifier = Modifier.clickable(onClick = onAddApp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, tint = OsColors.AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ajouter", color = OsColors.AccentCyan, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 28.dp,
        ) {
            if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune app enregistrée", color = OsColors.TextSecondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        LibraryAppRow(
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
private fun LibraryAppRow(
    app: AppBoxApp,
    onLaunch: () -> Unit,
    onRemove: () -> Unit,
) {
    GlassSurface(cornerRadius = 18.dp, backgroundAlpha = 0.1f, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                packageName = app.packageName,
                drawable = null,
                modifier = Modifier.size(48.dp),
                size = 48.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.displayName,
                    color = OsColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = OsColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        color = when (app.state) {
                            AppLifecycleState.ACTIVE -> OsColors.StatusActive
                            AppLifecycleState.SUSPENDED -> OsColors.StatusSuspended
                            else -> OsColors.StatusStopped
                        },
                        modifier = Modifier.size(7.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = app.state.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = OsColors.TextSecondary,
                    )
                }
            }
            IconButton(onClick = onLaunch) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Lancer", tint = OsColors.AccentCyan)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Retirer", tint = OsColors.StatusStopped)
            }
        }
    }
}
