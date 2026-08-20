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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.AgentLogEntry
import com.appbox.runtime.core.model.AgentState
import com.appbox.runtime.core.model.AgentStatus
import com.appbox.runtime.core.model.ConversationTurn
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
    agentState: AgentState,
    lastVoiceText: String?,
    conversationTurns: List<ConversationTurn>,
    hoshiConfig: HoshiUserConfig,
    openAiKeyConfigured: Boolean,
    accessibilityEnabled: Boolean,
    schedules: List<ScheduledTask>,
    runs: List<WorkflowRun>,
    logs: List<AgentLogEntry>,
    contactGroups: List<com.appbox.runtime.core.model.HoshiContactGroup>,
    onContactGroupsChange: (List<com.appbox.runtime.core.model.HoshiContactGroup>) -> Unit,
    onReloadInstructions: () -> Unit,
    onConfigChange: (HoshiUserConfig) -> Unit,
    onSaveConfig: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("HOSHI", color = AppBoxThemeColors.Accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    if (hoshiConfig.jarvisMode) {
                        "Assistant JARVIS — conversation, flows, exécution catalogue"
                    } else {
                        "Écoute active — dites « HOSHI » puis votre commande"
                    },
                    color = AppBoxThemeColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = AppBoxThemeColors.Surface,
            contentColor = AppBoxThemeColors.Accent,
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Conversation") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Configuration") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Activité") })
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> HoshiConversationPanel(
                conversationTurns = conversationTurns,
                lastVoiceText = lastVoiceText,
                agentStatus = agentState.status,
                jarvisMode = hoshiConfig.jarvisMode,
                modifier = Modifier.weight(1f),
            )
            1 -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                AppBoxPanel(cornerRadius = 8.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onReloadInstructions)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Settings, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recharger les instructions", color = AppBoxThemeColors.Accent, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HoshiConfigPanel(
                    config = hoshiConfig,
                    openAiKeyConfigured = openAiKeyConfigured,
                    accessibilityEnabled = accessibilityEnabled,
                    contactGroups = contactGroups,
                    onContactGroupsChange = onContactGroupsChange,
                    onConfigChange = onConfigChange,
                    onSave = onSaveConfig,
                    onOpenAccessibility = onOpenAccessibility,
                )
            }
            2 -> ActivityTab(
                schedules = schedules,
                runs = runs,
                logs = logs,
                accessibilityEnabled = accessibilityEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActivityTab(
    schedules: List<ScheduledTask>,
    runs: List<WorkflowRun>,
    logs: List<AgentLogEntry>,
    accessibilityEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    if (accessibilityEnabled) "JARVIS UI : contrôle écran activé" else "JARVIS UI : activez Accessibilité HOSHI",
                    color = if (accessibilityEnabled) Color(0xFF48BB78) else AppBoxThemeColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Planifications", color = AppBoxThemeColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                schedules.forEach { task ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Default.Schedule, null, tint = AppBoxThemeColors.TextTertiary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${task.name} · ${formatSchedule(task)}", color = AppBoxThemeColors.TextPrimary, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Dernières exécutions", color = AppBoxThemeColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                runs.takeLast(5).reversed().forEach { run ->
                    val color = when (run.status) {
                        WorkflowRunStatus.COMPLETED -> Color(0xFF48BB78)
                        WorkflowRunStatus.FAILED -> Color(0xFFFC8181)
                        else -> AppBoxThemeColors.TextTertiary
                    }
                    Text(
                        "${run.workflowId} — ${run.status.name}",
                        color = color,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                logs.takeLast(3).forEach {
                    Text(it.message, color = AppBoxThemeColors.TextTertiary, fontSize = 10.sp, maxLines = 2)
                }
            }
        }
    }
}

private fun formatSchedule(task: ScheduledTask): String = when (task.triggerType) {
    ScheduleTriggerType.DAILY_AT -> String.format("%02d:%02d", task.hour, task.minute)
    ScheduleTriggerType.ONE_SHOT -> SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(task.atEpochMs))
    ScheduleTriggerType.INTERVAL -> "intervalle"
}
