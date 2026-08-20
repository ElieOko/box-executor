package com.appbox.runtime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.ui.components.AppIcon
import com.appbox.runtime.ui.components.GlassSurface
import com.appbox.runtime.ui.theme.OsColors

@Composable
fun PermissionsScreen(
    apps: List<AppBoxApp>,
    selectedApp: AppBoxApp?,
    permissions: Set<RuntimePermission>,
    onSelectApp: (AppBoxApp?) -> Unit,
    onGrant: (String, RuntimePermission) -> Unit,
    onRevoke: (String, RuntimePermission) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Sécurité",
            style = MaterialTheme.typography.headlineMedium,
            color = OsColors.TextPrimary,
        )
        Text(
            text = "Contrôlez les permissions de chaque application",
            style = MaterialTheme.typography.bodyMedium,
            color = OsColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (apps.isEmpty()) {
            GlassSurface(modifier = Modifier.fillMaxSize(), cornerRadius = 28.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Ajoutez des applications pour gérer leurs permissions", color = OsColors.TextSecondary)
                }
            }
            return
        }

        GlassSurface(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(140.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val selected = selectedApp?.packageName == app.packageName
                    GlassSurface(
                        cornerRadius = 14.dp,
                        backgroundAlpha = if (selected) 0.22f else 0.08f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectApp(app) },
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(packageName = app.packageName, drawable = null, size = 36.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = app.displayName,
                                color = if (selected) OsColors.AccentCyan else OsColors.TextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        selectedApp?.let { app ->
            GlassSurface(modifier = Modifier.fillMaxSize(), cornerRadius = 28.dp) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        Text(
                            text = "Permissions — ${app.displayName}",
                            color = OsColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    items(RuntimePermission.entries.toList()) { permission ->
                        PermissionToggleRow(
                            permission = permission,
                            granted = permissions.contains(permission),
                            onToggle = { enabled ->
                                if (enabled) onGrant(app.packageName, permission)
                                else onRevoke(app.packageName, permission)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    permission: RuntimePermission,
    granted: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = permission.name.replace('_', ' '),
            style = MaterialTheme.typography.bodyMedium,
            color = OsColors.TextPrimary,
        )
        Switch(
            checked = granted,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OsColors.AccentCyan,
                checkedTrackColor = OsColors.AccentCyan.copy(alpha = 0.35f),
                uncheckedThumbColor = OsColors.TextMuted,
                uncheckedTrackColor = OsColors.GlassWhite,
            ),
        )
    }
}
