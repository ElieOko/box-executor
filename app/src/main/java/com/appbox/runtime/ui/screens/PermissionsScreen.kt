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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.components.AppIcon
import com.appbox.runtime.ui.theme.AppBoxThemeColors

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
        if (apps.isEmpty()) {
            AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 20.dp) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Ajoutez des applications d'abord", color = AppBoxThemeColors.TextSecondary)
                }
            }
            return
        }

        AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
            LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(120.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    val selected = selectedApp?.packageName == app.packageName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectApp(app) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(packageName = app.packageName, drawable = null, size = 32.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = app.displayName,
                            color = if (selected) AppBoxThemeColors.Accent else AppBoxThemeColors.TextPrimary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        selectedApp?.let { app ->
            AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 20.dp) {
                LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item {
                        Text(
                            text = app.displayName,
                            color = AppBoxThemeColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    items(RuntimePermission.entries.toList()) { permission ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                permission.name.replace('_', ' '),
                                color = AppBoxThemeColors.TextSecondary,
                                fontSize = 13.sp,
                            )
                            Switch(
                                checked = permissions.contains(permission),
                                onCheckedChange = { enabled ->
                                    if (enabled) onGrant(app.packageName, permission)
                                    else onRevoke(app.packageName, permission)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppBoxThemeColors.Accent,
                                    checkedTrackColor = AppBoxThemeColors.AccentSoft,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
