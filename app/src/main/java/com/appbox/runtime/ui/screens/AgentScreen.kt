package com.appbox.runtime.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.AgentLogEntry
import com.appbox.runtime.core.model.AgentState
import com.appbox.runtime.core.model.HoshiUserConfig
import com.appbox.runtime.core.model.ScheduleTriggerType
import com.appbox.runtime.core.model.ScheduledTask
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowRun
import com.appbox.runtime.core.model.WorkflowRunStatus
import com.appbox.runtime.ui.HoshiAgentTab
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.theme.AppBoxThemeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgentScreen(
    agentState: AgentState,
    workflows: List<WorkflowDefinition>,
    selectedWorkflow: WorkflowDefinition?,
    schedules: List<ScheduledTask>,
    runs: List<WorkflowRun>,
    logs: List<AgentLogEntry>,
    isListening: Boolean,
    lastVoiceText: String?,
    hoshiConfig: HoshiUserConfig,
    workflowEditMode: Boolean,
    accessibilityEnabled: Boolean,
    agentTab: HoshiAgentTab,
    onSelectWorkflow: (WorkflowDefinition) -> Unit,
    onRunWorkflow: (String) -> Unit,
    onToggleVoice: () -> Unit,
    onReloadInstructions: () -> Unit,
    onConfigChange: (HoshiUserConfig) -> Unit,
    onSaveConfig: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onTabChange: (HoshiAgentTab) -> Unit,
    onToggleEditMode: () -> Unit,
    onNodeMoved: (String, Float, Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HoshiHeader(
            isListening = isListening,
            voiceContinuous = hoshiConfig.voiceContinuous,
            onToggleVoice = onToggleVoice,
            onReload = onReloadInstructions,
        )

        HoshiTabRow(current = agentTab, onTabChange = onTabChange)

        Spacer(modifier = Modifier.height(8.dp))

        when (agentTab) {
            HoshiAgentTab.CONFIG -> Column(
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
            HoshiAgentTab.FLOWS -> Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(0.35f)) {
                    Text("Workflows HOSHI", color = AppBoxThemeColors.TextSecondary, fontSize = 12.sp)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(workflows, key = { it.id }) { workflow ->
                            WorkflowListItem(
                                workflow = workflow,
                                selected = selectedWorkflow?.id == workflow.id,
                                onClick = { onSelectWorkflow(workflow) },
                                onRun = { onRunWorkflow(workflow.id) },
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(0.65f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selectedWorkflow?.name ?: "Flow",
                            color = AppBoxThemeColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                        AppBoxPanel(
                            cornerRadius = 8.dp,
                            modifier = Modifier.clickable(onClick = onToggleEditMode),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    tint = if (workflowEditMode) AppBoxThemeColors.Accent else AppBoxThemeColors.TextTertiary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (workflowEditMode) "Édition" else "Éditer",
                                    color = if (workflowEditMode) AppBoxThemeColors.Accent else AppBoxThemeColors.TextSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    if (selectedWorkflow != null) {
                        AppBoxPanel(modifier = Modifier.weight(1f), cornerRadius = 16.dp) {
                            DraggableWorkflowCanvas(
                                workflow = selectedWorkflow,
                                editMode = workflowEditMode,
                                onNodeMoved = onNodeMoved,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HoshiFooter(schedules, runs, logs, lastVoiceText)
    }
}

@Composable
private fun HoshiHeader(
    isListening: Boolean,
    voiceContinuous: Boolean,
    onToggleVoice: () -> Unit,
    onReload: () -> Unit,
) {
    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("HOSHI", color = AppBoxThemeColors.Accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    when {
                        isListening && voiceContinuous -> "Écoute continue — dites « HOSHI » + commande"
                        isListening -> "Écoute active"
                        else -> "Voix en pause"
                    },
                    color = AppBoxThemeColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isListening) Color(0xFF48BB78) else AppBoxThemeColors.SurfaceHover)
                        .clickable(onClick = onToggleVoice),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voix HOSHI",
                        tint = if (isListening) Color.White else AppBoxThemeColors.Accent,
                    )
                }
                AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.clickable(onClick = onReload)) {
                    Text("Recharger", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = AppBoxThemeColors.Accent, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HoshiTabRow(current: HoshiAgentTab, onTabChange: (HoshiAgentTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(HoshiAgentTab.CONFIG to "Config", HoshiAgentTab.FLOWS to "Flows").forEach { (tab, label) ->
            AppBoxPanel(
                cornerRadius = 10.dp,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabChange(tab) },
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    color = if (current == tab) AppBoxThemeColors.Accent else AppBoxThemeColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (current == tab) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun WorkflowListItem(
    workflow: WorkflowDefinition,
    selected: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
) {
    AppBoxPanel(
        cornerRadius = 12.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workflow.name, color = if (selected) AppBoxThemeColors.Accent else AppBoxThemeColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${workflow.nodes.size} nœuds", color = AppBoxThemeColors.TextTertiary, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(AppBoxThemeColors.Accent.copy(alpha = 0.15f)).clickable(onClick = onRun),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HoshiFooter(
    schedules: List<ScheduledTask>,
    runs: List<WorkflowRun>,
    logs: List<AgentLogEntry>,
    lastVoiceText: String?,
) {
    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            lastVoiceText?.let {
                Text("Entendu : \"$it\"", color = AppBoxThemeColors.Accent, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }
            schedules.take(2).forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = AppBoxThemeColors.TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${task.name} · ${formatSchedule(task)}", color = AppBoxThemeColors.TextPrimary, fontSize = 11.sp)
                }
            }
            runs.takeLast(2).reversed().forEach { run ->
                val color = when (run.status) {
                    WorkflowRunStatus.COMPLETED -> Color(0xFF48BB78)
                    WorkflowRunStatus.FAILED -> Color(0xFFFC8181)
                    else -> AppBoxThemeColors.TextTertiary
                }
                Text("${run.workflowId} — ${run.status.name}", color = color, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            logs.takeLast(1).forEach { Text(it.message, color = AppBoxThemeColors.TextTertiary, fontSize = 10.sp, maxLines = 1) }
        }
    }
}

private fun formatSchedule(task: ScheduledTask): String = when (task.triggerType) {
    ScheduleTriggerType.DAILY_AT -> String.format("%02d:%02d", task.hour, task.minute)
    ScheduleTriggerType.ONE_SHOT -> SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(task.atEpochMs))
    ScheduleTriggerType.INTERVAL -> "intervalle"
}
