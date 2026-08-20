package com.appbox.runtime.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.TrackedProcess
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.components.StatusIndicator
import com.appbox.runtime.ui.theme.AppBoxThemeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(
    events: List<RemoteMonitorEvent>,
    processes: List<TrackedProcess>,
    hasUsageAccess: Boolean,
    canDrawOverlay: Boolean,
    isLockTaskActive: Boolean,
    isDeviceOwner: Boolean,
    onRequestUsageAccess: () -> Unit,
    onRequestOverlay: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HostSetupPanel(
            hasUsageAccess = hasUsageAccess,
            canDrawOverlay = canDrawOverlay,
            isLockTaskActive = isLockTaskActive,
            isDeviceOwner = isDeviceOwner,
            onRequestUsageAccess = onRequestUsageAccess,
            onRequestOverlay = onRequestOverlay,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text("Processus encadrés (ActivityManager)", color = AppBoxThemeColors.TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))

        AppBoxPanel(modifier = Modifier.weight(1f), cornerRadius = 16.dp) {
            if (processes.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(
                        "Aucun processus actif",
                        color = AppBoxThemeColors.TextTertiary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(processes, key = { "${it.packageName}_${it.pid}" }) { proc ->
                        ProcessRow(proc)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Journal", color = AppBoxThemeColors.TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))

        AppBoxPanel(modifier = Modifier.weight(1f), cornerRadius = 16.dp) {
            if (events.isEmpty()) {
                Text("Aucun événement", color = AppBoxThemeColors.TextTertiary, modifier = Modifier.padding(16.dp))
            } else {
                val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(events.reversed(), key = { "${it.timestamp}_${it.type}" }) { event ->
                        Column(modifier = Modifier.padding(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    event.type.name,
                                    color = AppBoxThemeColors.Accent,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    fmt.format(Date(event.timestamp)),
                                    color = AppBoxThemeColors.TextTertiary,
                                    fontSize = 11.sp,
                                )
                            }
                            event.packageName?.let {
                                Text(it, color = AppBoxThemeColors.TextSecondary, fontSize = 11.sp)
                            }
                            Text(event.message, color = AppBoxThemeColors.TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostSetupPanel(
    hasUsageAccess: Boolean,
    canDrawOverlay: Boolean,
    isLockTaskActive: Boolean,
    isDeviceOwner: Boolean,
    onRequestUsageAccess: () -> Unit,
    onRequestOverlay: () -> Unit,
) {
    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Configuration hôte AppBox",
                color = AppBoxThemeColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SetupRow("Lock Task", if (isLockTaskActive) "Actif" else "Inactif", isLockTaskActive)
            SetupRow("Device Owner", if (isDeviceOwner) "Oui" else "Non (requis pour kiosque complet)", isDeviceOwner)
            SetupRow("Usage stats", if (hasUsageAccess) "OK" else "Requis", hasUsageAccess)
            SetupRow("Overlay retour", if (canDrawOverlay) "OK" else "Requis si app sort de la box", canDrawOverlay)

            if (!hasUsageAccess || !canDrawOverlay) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    if (!hasUsageAccess) {
                        TextButton(onClick = onRequestUsageAccess) {
                            Text("Usage", color = AppBoxThemeColors.Accent, fontSize = 12.sp)
                        }
                    }
                    if (!canDrawOverlay) {
                        TextButton(onClick = onRequestOverlay) {
                            Text("Overlay", color = AppBoxThemeColors.Accent, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Pour un hôte complet : provisionner AppBox en Device Owner (voir HOST_SETUP.md), " +
                    "ajouter vos APK dans AppBox, activer overlay + usage stats.",
                color = AppBoxThemeColors.TextTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun SetupRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppBoxThemeColors.TextSecondary, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIndicator(active = ok)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                value,
                color = if (ok) AppBoxThemeColors.Success else AppBoxThemeColors.Warning,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ProcessRow(proc: TrackedProcess) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIndicator(active = proc.isForeground)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    proc.displayName,
                    color = AppBoxThemeColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
            Text(
                "PID ${proc.pid} · UID ${proc.uid} · ${proc.importanceLabel}",
                color = AppBoxThemeColors.TextTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            "${proc.memoryPssKb} Ko",
            color = AppBoxThemeColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
