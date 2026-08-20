package com.appbox.runtime.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.AgentLogEntry
import com.appbox.runtime.core.model.AgentState
import com.appbox.runtime.core.model.AgentStatus
import com.appbox.runtime.core.model.ScheduleTriggerType
import com.appbox.runtime.core.model.ScheduledTask
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowEdge
import com.appbox.runtime.core.model.WorkflowNode
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
    workflows: List<WorkflowDefinition>,
    selectedWorkflow: WorkflowDefinition?,
    schedules: List<ScheduledTask>,
    runs: List<WorkflowRun>,
    logs: List<AgentLogEntry>,
    isListening: Boolean,
    lastVoiceText: String?,
    onSelectWorkflow: (WorkflowDefinition) -> Unit,
    onRunWorkflow: (String) -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onReloadInstructions: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AgentHeader(
            state = agentState,
            isListening = isListening,
            onStartVoice = onStartVoice,
            onStopVoice = onStopVoice,
            onReload = onReloadInstructions,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(0.38f)) {
                Text(
                    "Workflows",
                    color = AppBoxThemeColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
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

            Column(modifier = Modifier.weight(0.62f)) {
                if (selectedWorkflow != null) {
                    Text(
                        "Flow — ${selectedWorkflow.name}",
                        color = AppBoxThemeColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                    AppBoxPanel(modifier = Modifier.weight(1f), cornerRadius = 16.dp) {
                        WorkflowFlowCanvas(
                            workflow = selectedWorkflow,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AgentFooter(
            schedules = schedules,
            runs = runs,
            logs = logs,
            lastVoiceText = lastVoiceText,
        )
    }
}

@Composable
private fun AgentHeader(
    state: AgentState,
    isListening: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
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
                Text(
                    state.name,
                    color = AppBoxThemeColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text(
                    "Statut: ${state.status.name} · ${state.pendingTasks} planification(s)",
                    color = AppBoxThemeColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening) AppBoxThemeColors.Accent else AppBoxThemeColors.SurfaceHover,
                        )
                        .clickable {
                            if (isListening) onStopVoice() else onStartVoice()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voix",
                        tint = if (isListening) Color.White else AppBoxThemeColors.Accent,
                    )
                }
                AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.clickable(onClick = onReload)) {
                    Text(
                        "Recharger",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = AppBoxThemeColors.Accent,
                        fontSize = 12.sp,
                    )
                }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    workflow.name,
                    color = if (selected) AppBoxThemeColors.Accent else AppBoxThemeColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${workflow.nodes.size} nœuds · ${workflow.edges.size} liens",
                    color = AppBoxThemeColors.TextTertiary,
                    fontSize = 11.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppBoxThemeColors.Accent.copy(alpha = 0.15f))
                    .clickable(onClick = onRun),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun WorkflowFlowCanvas(
    workflow: WorkflowDefinition,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val nodeWidth = 140.dp
    val nodeHeight = 56.dp

    Box(
        modifier = modifier.verticalScroll(scrollState),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            workflow.edges.forEach { edge ->
                val from = workflow.nodes.find { it.id == edge.fromNodeId } ?: return@forEach
                val to = workflow.nodes.find { it.id == edge.toNodeId } ?: return@forEach
                val start = Offset(from.positionX + nodeWidth.toPx() / 2, from.positionY + nodeHeight.toPx() / 2)
                val end = Offset(to.positionX + nodeWidth.toPx() / 2, to.positionY + nodeHeight.toPx() / 2)
                drawLine(
                    color = Color(0xFF4A5568),
                    start = start,
                    end = end,
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                )
            }
        }

        workflow.nodes.forEach { node ->
            FlowNodeChip(
                node = node,
                modifier = Modifier.offset(node.positionX.dp, node.positionY.dp),
            )
        }
    }
}

@Composable
private fun FlowNodeChip(node: WorkflowNode, modifier: Modifier = Modifier) {
    val color = when {
        node.type.name.startsWith("TRIGGER_") -> Color(0xFF2B6CB0)
        node.type.name.contains("WHATSAPP") -> Color(0xFF25D366)
        node.type.name.contains("HTTP") || node.type.name.contains("HN") -> Color(0xFFDD6B20)
        else -> Color(0xFF4A5568)
    }

    Box(
        modifier = modifier
            .width(140.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.25f))
            .padding(8.dp),
    ) {
        Column {
            Text(
                node.label.ifBlank { node.type.name },
                color = AppBoxThemeColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                node.type.name.replace('_', ' '),
                color = AppBoxThemeColors.TextTertiary,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun AgentFooter(
    schedules: List<ScheduledTask>,
    runs: List<WorkflowRun>,
    logs: List<AgentLogEntry>,
    lastVoiceText: String?,
) {
    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            lastVoiceText?.let {
                Text("Dernière voix: \"$it\"", color = AppBoxThemeColors.Accent, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text("Planifications", color = AppBoxThemeColors.TextSecondary, fontSize = 11.sp)
            schedules.take(3).forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = AppBoxThemeColors.TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${task.name} → ${formatSchedule(task)}",
                        color = AppBoxThemeColors.TextPrimary,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Exécutions récentes", color = AppBoxThemeColors.TextSecondary, fontSize = 11.sp)
            runs.takeLast(3).reversed().forEach { run ->
                val color = when (run.status) {
                    WorkflowRunStatus.COMPLETED -> Color(0xFF48BB78)
                    WorkflowRunStatus.FAILED -> Color(0xFFFC8181)
                    WorkflowRunStatus.RUNNING -> AppBoxThemeColors.Accent
                    else -> AppBoxThemeColors.TextTertiary
                }
                Text(
                    "${run.workflowId} — ${run.status.name}${run.error?.let { ": $it" } ?: ""}",
                    color = color,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    logs.takeLast(2).joinToString(" · ") { it.message },
                    color = AppBoxThemeColors.TextTertiary,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatSchedule(task: ScheduledTask): String = when (task.triggerType) {
    ScheduleTriggerType.DAILY_AT -> String.format("%02d:%02d", task.hour, task.minute)
    ScheduleTriggerType.ONE_SHOT -> task.atEpochMs.let {
        SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(it))
    }
    ScheduleTriggerType.INTERVAL -> "toutes les ${task.intervalMs / 3600_000}h"
}
