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
    onRequestUsageAccess: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!hasUsageAccess) {
            AppBoxPanel(cornerRadius = 12.dp, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Accès usage requis",
                            color = AppBoxThemeColors.TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                        Text(
                            "Autorisez l'accès aux statistiques d'utilisation pour un suivi précis du premier plan.",
                            color = AppBoxThemeColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = onRequestUsageAccess) {
                        Text("Autoriser", color = AppBoxThemeColors.Accent)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

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
