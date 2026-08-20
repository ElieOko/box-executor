package com.appbox.runtime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.AgentLogEntry
import com.appbox.runtime.core.model.HoshiUserConfig
import com.appbox.runtime.core.model.ScheduleTriggerType
import com.appbox.runtime.core.model.ScheduledTask
import com.appbox.runtime.core.model.WorkflowRun
import com.appbox.runtime.core.model.WorkflowRunStatus
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.theme.AppBoxThemeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgentScreen(
    lastVoiceText: String?,
    hoshiConfig: HoshiUserConfig,
    accessibilityEnabled: Boolean,
    schedules: List<ScheduledTask>,
    runs: List<WorkflowRun>,
    logs: List<AgentLogEntry>,
    onReloadInstructions: () -> Unit,
    onConfigChange: (HoshiUserConfig) -> Unit,
    onSaveConfig: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("HOSHI", color = AppBoxThemeColors.Accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "Écoute active en permanence — dites « HOSHI » puis votre commande",
                    color = AppBoxThemeColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppBoxPanel(cornerRadius = 8.dp, modifier = Modifier.clickable(onClick = onReloadInstructions)) {
                    Text(
                        "Recharger les instructions",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = AppBoxThemeColors.Accent,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            HoshiConfigPanel(
                config = hoshiConfig,
                accessibilityEnabled = accessibilityEnabled,
                onConfigChange = onConfigChange,
                onSave = onSaveConfig,
                onOpenAccessibility = onOpenAccessibility,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        HoshiStatusFooter(schedules, runs, logs, lastVoiceText, accessibilityEnabled)
    }
}

@Composable
private fun HoshiStatusFooter(
    schedules: List<ScheduledTask>,
    runs: List<WorkflowRun>,
    logs: List<AgentLogEntry>,
    lastVoiceText: String?,
    accessibilityEnabled: Boolean,
) {
    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                if (accessibilityEnabled) "WhatsApp : envoi automatique activé" else "WhatsApp : activez Accessibilité HOSHI",
                color = if (accessibilityEnabled) Color(0xFF48BB78) else AppBoxThemeColors.Accent,
                fontSize = 11.sp,
            )
            lastVoiceText?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Entendu : \"$it\"", color = AppBoxThemeColors.TextSecondary, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            schedules.take(2).forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = AppBoxThemeColors.TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${task.name} · ${formatSchedule(task)}", color = AppBoxThemeColors.TextPrimary, fontSize = 11.sp)
                }
            }
            runs.takeLast(1).forEach { run ->
                val color = when (run.status) {
                    WorkflowRunStatus.COMPLETED -> Color(0xFF48BB78)
                    WorkflowRunStatus.FAILED -> Color(0xFFFC8181)
                    else -> AppBoxThemeColors.TextTertiary
                }
                Text("${run.workflowId} — ${run.status.name}", color = color, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            logs.takeLast(1).forEach {
                Text(it.message, color = AppBoxThemeColors.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun formatSchedule(task: ScheduledTask): String = when (task.triggerType) {
    ScheduleTriggerType.DAILY_AT -> String.format("%02d:%02d", task.hour, task.minute)
    ScheduleTriggerType.ONE_SHOT -> SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(task.atEpochMs))
    ScheduleTriggerType.INTERVAL -> "intervalle"
}
