package com.appbox.runtime.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowNode
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun HoshiFlowsScreen(
    workflows: List<WorkflowDefinition>,
    selectedWorkflow: WorkflowDefinition?,
    editMode: Boolean,
    onSelectWorkflow: (WorkflowDefinition) -> Unit,
    onRunWorkflow: (String) -> Unit,
    onToggleEditMode: () -> Unit,
    onNodeMoved: (String, Float, Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Flows HOSHI", color = AppBoxThemeColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Glissez horizontalement pour voir la suite du parcours →",
                    color = AppBoxThemeColors.Accent.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
            AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.clickable(onClick = onToggleEditMode)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (editMode) AppBoxThemeColors.Accent else AppBoxThemeColors.TextTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (editMode) "Édition" else "Éditer",
                        color = if (editMode) AppBoxThemeColors.Accent else AppBoxThemeColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(0.30f)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(workflows, key = { it.id }) { workflow ->
                        FlowListItem(
                            workflow = workflow,
                            selected = selectedWorkflow?.id == workflow.id,
                            onClick = { onSelectWorkflow(workflow) },
                            onRun = { onRunWorkflow(workflow.id) },
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(0.70f)) {
                if (selectedWorkflow != null) {
                    FlowPathPreview(workflow = selectedWorkflow)
                    Spacer(modifier = Modifier.height(6.dp))
                    AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 16.dp) {
                        DraggableWorkflowCanvas(
                            workflow = selectedWorkflow,
                            editMode = editMode,
                            onNodeMoved = onNodeMoved,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                        )
                    }
                } else {
                    AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 16.dp) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SwipeRight, null, tint = AppBoxThemeColors.TextTertiary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Sélectionnez un flow", color = AppBoxThemeColors.TextTertiary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowPathPreview(workflow: WorkflowDefinition) {
    val orderedNodes = remember(workflow.id, workflow.edges) {
        val outgoing = workflow.edges.groupBy { it.fromNodeId }
        val start = workflow.nodes.firstOrNull { it.type.name.startsWith("TRIGGER_") }?.id
            ?: workflow.nodes.firstOrNull()?.id
        val path = mutableListOf<WorkflowNode>()
        var current = start
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current)) {
            workflow.nodes.find { it.id == current }?.let { path += it }
            current = outgoing[current]?.firstOrNull()?.toNodeId
        }
        path.ifEmpty { workflow.nodes.take(4) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        orderedNodes.forEachIndexed { index, node ->
            if (index > 0) {
                Text("→", color = AppBoxThemeColors.Accent, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1E3A5F), Color(0xFF172554)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    node.label.ifBlank { node.type.name.replace('_', ' ') },
                    color = AppBoxThemeColors.TextPrimary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FlowListItem(
    workflow: WorkflowDefinition,
    selected: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) AppBoxThemeColors.AccentSoft else AppBoxThemeColors.SurfaceElevated,
        tween(250),
        label = "flowItemBg",
    )

    AppBoxPanel(
        cornerRadius = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .background(bg)
                .padding(10.dp),
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
                    "${workflow.nodes.size} étapes · ${workflow.edges.size} liens",
                    color = AppBoxThemeColors.TextTertiary,
                    fontSize = 10.sp,
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
